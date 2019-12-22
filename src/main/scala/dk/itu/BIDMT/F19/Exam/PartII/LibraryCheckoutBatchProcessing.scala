package dk.itu.BIDMT.F19.Exam.PartII

import com.typesafe.config.ConfigFactory
import org.apache.log4j.Level.WARN
import org.apache.log4j.LogManager
import org.apache.spark.sql.{DataFrame, Row, SparkSession}
import org.apache.spark.sql.functions._

object LibraryCheckoutBatchProcessing {

  //create a spark session
  val spark = SparkSession
    .builder()
    .appName("LibraryCheckoutBatchProcessing")
    // .master("local[4]")
    .getOrCreate()

  import spark.implicits._

  val log = LogManager.getRootLogger
  log.setLevel(WARN)


  /**
    * Code to load the data from a csv file given the file path of that file
    *
    * Hint: you can allow spark to infer the schema
    * (or you can define your own schema based on the information from kaggle)
    * Note that the first row in the data is a header!
    *
    * @param path
    * @return DataFrame of the loaded data
    */
  def dataLoader(path: String): DataFrame = {
    spark
      .read
      .format("csv")
      .option("header","true")
      .option("inferSchema", "true")
      .load(path)
      .toDF
  }

  /**
    * Q1
    * query the library inventory to find the number of items in the inventory written/created by each author
    *
    * @param libraryInventoryDF
    * @return A DaraFrame of two columns: Author, NumPublications
    */
  def libraryItemsPerAuthor(libraryInventoryDF: DataFrame): DataFrame = {
    //ItemCount =  number of items in this location, collection, item type, and item status as of the report date.
    val df = libraryInventoryDF
      .filter($"Author".isNotNull)
      .select("BibNum", "Author", "ItemCount")
      .groupBy($"Author")
      .agg(sum("ItemCount").as("NumPublications"))
    df
  }


  /**
    * Q2
    * query the checkout records and the library dictionary to find
    * the number of checked out items per Fromat Group - Format Subgroup pair
    *
    * Note that multiple ItemType code could have the same Format Group: Format Subgroup combination
    * drmfmnp,	Microfilm: Dummy Newspaper,	ItemType,	Media,	Film, ,
    * drmfper,	Microfilm: Dummy Periodical,	ItemType,	Media,	Film, ,
    *
    * Hint: You might want to create a udf to combine the two columns "Format Group" and "Format Subgroup"
    * to one column "Format".
    * The output in the Format column should be in the form of  Format Group:Format Subgroup
    * if the value in  Format Subgroup is not null, else it should also be the value in Format Group column
    * You need to account for null values
    * Examples of the values in the o/p Format column: "Media:Audio Disc"  and "Equipment"
    *
    * @param checkoutDF
    * @param dataDictionaryDF
    * @return A DataFrame of two columns: Format,CheckoutCount
    */
  def numberCheckoutRecordsPerFormat(checkoutDF: DataFrame, dataDictionaryDF: DataFrame): DataFrame = {
    //Define a udf to concatenate two passed in string values in the columns we want to concatenate
    //if subgroup is a null value, set Format value only to first parameter which represents format group
    val getConcat = udf( (first: String, second: String) =>
    { second match {
      case null => first
      case _ =>
        first + " : " + second
    }
    } )
    //use withColumn method to add a new column called Format
    val df = dataDictionaryDF
      .filter($"Format Group".isNotNull)
      .withColumn("Format", getConcat($"Format Group", $"Format Subgroup"))
      .select("Code", "Description", "Code Type", "Format", "Category Group", "Category Subgroup")
    df
      .join(checkoutDF).where($"Code" === $"ItemType")
      .groupBy($"Format")
      .agg(count("*"))
      .withColumnRenamed("count(1)", "CheckoutCount")
  }


  /**
    * Q3
    * query the checkout records and the library inventory details to
    * find the top k library locations where the most checkouts happened
    *
    * The values stored in the ItemLocation column of the library inventory file is a code.
    * Therefore, you will need to decode the location from the description found in the library dictionary
    * Note: for codes that represent library locations, the value in Code Type column is "ItemLocation"
    * and the details of the location are in the Description column
    *
    * @param checkoutDF
    * @param libraryInventoryDF
    * @param dataDictionaryDF
    * @param k
    * @return A DataFrame of two columns: ItemLocationDescription, NumCheckoutItemsAtLocation - num of records in this dataframe is equal to k
    */
  def topKCheckoutLocations(checkoutDF: DataFrame, libraryInventoryDF: DataFrame, dataDictionaryDF: DataFrame, k: Int): DataFrame = {

    val locs = dataDictionaryDF
      .filter($"Code Type" === "ItemLocation")
      .select("Code", "Description", "Code Type")

    val inv = libraryInventoryDF
      .as("inv")
      .join(locs.as("l"), $"l.Code" === $"inv.ItemLocation")

    val num = checkoutDF
      .as("c")
      .join(inv.as("i"), $"c.BibNumber" === $"i.BibNum")
      .groupBy($"i.Code")
      .agg(count("*"))
      .withColumnRenamed("count(1)","NumCheckoutItemsAtLocation")
      .sort(desc("NumCheckoutItemsAtLocation"))//.persist()

    val top = num
      .as("c")
      .join(locs.as("l"), $"c.Code" === $"l.Code")
      .withColumnRenamed("Description", "ItemLocationDescription")
      .select("ItemLocationDescription", "NumCheckoutItemsAtLocation")
      .limit(k)
    top.persist()
  }




  def main(args: Array[String]): Unit = {
    //load configuration
    val config = ConfigFactory.load()

    //check that the paths for input files exist in the config, otherwise exit
    if (!config.hasPath("BIDMT.Exam.Batch.checkoutData") ||
      !config.hasPath("BIDMT.Exam.Batch.dataDictionary") ||
      !config.hasPath("BIDMT.Exam.Batch.libraryInventory")) {
      println("Error, configuration file does not contain the file paths for input datasets!")
      spark.close()
    }

    //read input file paths from the configuration file
    val checkoutFilePath = config.getString("BIDMT.Exam.Batch.checkoutData")
    val dataDictionaryFilePath = config.getString("BIDMT.Exam.Batch.dataDictionary")
    val libraryInventoryFilePath = config.getString("BIDMT.Exam.Batch.libraryInventory")

    //read output files path from the configuration file
    val outFilesPath = config.getString("BIDMT.Exam.Batch.outPath")


    if (args.length == 0) {
      //call all queries
      //load data
      val checkoutDF = dataLoader(checkoutFilePath).persist
      val dataDictionaryDF = dataLoader(dataDictionaryFilePath).persist
      val libraryInventoryDF = dataLoader(libraryInventoryFilePath).persist

      //read value of k from configuration file
      val numLibraryLocations =
        if (config.hasPath("BIDMT.Exam.Batch.numLibraryLocations"))
          config.getInt("BIDMT.Exam.Batch.numLibraryLocations")
        else 30 //we are setting the default value to 30

      /*  val x = libraryItemsPerAuthor(libraryInventoryDF)
        x.show(5)

        val y = numberCheckoutRecordsPerFormat(checkoutDF, dataDictionaryDF)
        y.show(10)

        val z = topKCheckoutLocations(checkoutDF, libraryInventoryDF, dataDictionaryDF, 10)
        z.show(27)

        // Using time function from http://biercoff.com/easily-measuring-code-execution-time-in-scala/:
        def time[R](block: => R): R = {
          val t0 = System.currentTimeMillis()
          val result = block    // call-by-name
          val t1 = System.currentTimeMillis()
          println("Elapsed time: " + (t1 - t0) + "ms" + " for " + block )
          result
        }

       println("runtime q1 " )
        val timeQ1 = time (libraryItemsPerAuthor(libraryInventoryDF))
        timeQ1.show()

        println("runtime q2 " )
        val timeQ2 = time (numberCheckoutRecordsPerFormat(checkoutDF, dataDictionaryDF))
        timeQ2.show()

        println("runtime q3 " )
        val timeQ3 = time (topKCheckoutLocations(checkoutDF, libraryInventoryDF, dataDictionaryDF, 10))
        timeQ3.show()*/

      //Query 1
      libraryItemsPerAuthor(libraryInventoryDF)
        .write
        .mode("overwrite")
        .csv(outFilesPath + "/q1")
      //Query 2
      numberCheckoutRecordsPerFormat(checkoutDF, dataDictionaryDF)
        .write
        .mode("overwrite")
        .csv(outFilesPath + "/q2")
      //Query 3
      topKCheckoutLocations(checkoutDF, libraryInventoryDF, dataDictionaryDF, numLibraryLocations)
        .write
        .mode("overwrite")
        .csv(outFilesPath + "/q3")

    } else {
      args(0).toInt match {
        case 1 => {
          //Query 1
          //load data
          val libraryInventoryDF = dataLoader(libraryInventoryFilePath)
          libraryItemsPerAuthor(libraryInventoryDF)
            .write
            .mode("overwrite")
            .csv(outFilesPath + "/q1")
        }
        case 2 => {
          //Query 2
          //load data
          val checkoutDF = dataLoader(checkoutFilePath)
          val dataDictionaryDF = dataLoader(dataDictionaryFilePath)
          numberCheckoutRecordsPerFormat(checkoutDF, dataDictionaryDF)
            .write
            .mode("overwrite")
            .csv(outFilesPath + "/q2")
        }
        case 3 => {
          //Query 3
          //load data
          val checkoutDF = dataLoader(checkoutFilePath)
          val dataDictionaryDF = dataLoader(dataDictionaryFilePath)
          val libraryInventoryDF = dataLoader(libraryInventoryFilePath)
          //read value of k from configuration file
          val numLibraryLocations =
            if (config.hasPath("BIDMT.Exam.Batch.numLibraryLocations"))
              config.getInt("BIDMT.Exam.Batch.numLibraryLocations")
            else 30 //we are setting the default value to 30

          topKCheckoutLocations(checkoutDF, libraryInventoryDF, dataDictionaryDF, numLibraryLocations)
            .write
            .mode("overwrite")
            .csv(outFilesPath + "/q3")
        }
        case _ => println("Usage for LibraryCheckoutBatchProcessing: Optional selected query can be 1, 2, or 3")
      }

    }

    //stop spark
    spark.close()
  }
}
