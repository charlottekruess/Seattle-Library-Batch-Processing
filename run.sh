spark-submit \
  --master yarn \
  --deploy-mode cluster \
  --class dk.itu.BIDMT.F19.Exam.PartII.LibraryCheckoutBatchProcessing \
  --files application.conf \
  --conf spark.driver.extraJavaOptions=-Dconfig.file=application.conf \
  --conf spark.executor.extraJavaOptions=-Dconfig.file=application.conf \
  BIDM_F19_Exam-assembly-0.1.jar