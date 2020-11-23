## Seattle-Library-Batch-Processing
This project was developed in relation to the big data management course at the IT University of Copenhagen. 
It is designed as a Apache Spark application to run queries efficiently on a 11GB large dataset, specifically the [Seattle Library Collection Inventory](https://www.kaggle.com/city-of-seattle/seattle-library-collection-inventory). Utilizing Spark Dataframes, the application handles 3 different queries:
+ Q1: Finding total number of items in the library inventory per author in the Input Dataset
+ Q2: Finding the total number of checkout occurrences for eachitem type specified by a Format (Format Group + Format SubGroup)
+ Q3: Finding the top k locations that have the highest numbersof checkout records

The application was tested on different data volumes, meaning different ranges of year records, e.g. only processing records from 2005 or all records from 2005-2017.
An AWS cluster instance of m5.xlarge with a total size of 64GB, comprising 1 master and 3 core nodes was used to assess the performance for the entire application and each query separately. 
Increasing the data volume significantly effects the run time, however it doesn’t increase linearly. A 27x larger data set results in a roughly 3x longer run time. 
The use of persist() allowed to cache DataFrames in memory which slightly improved the run time when running the entire application on the largest tested data set. 
