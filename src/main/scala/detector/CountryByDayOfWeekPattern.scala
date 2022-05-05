package detector

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._
import org.joda.time.DateTime

object CountryByDayOfWeekPattern {

	/**
	  * Tests for a pattern in the purchase frequency for each country by day of week.  (Assumes pre-normalized time zone data.)
	  *
	  * @param data	Dataframe to search for a pattern on.
	  * @return		Search result as `Option[String]`.  (`None` = no pattern)
	  */
	def Go(data: DataFrame): Option[String] = {
		var newDf = data  // Generate the "average_count" and "average_total" data
			.select("country", "datetime", "qty")
			.withColumn("day_of_week", date_format(col("datetime"), "EEEE"))  // Create a column with the day of week name for each date
			.groupBy("country", "day_of_week")
			.agg(count("day_of_week").as("count"), sum("qty").as("total"))
			.withColumn("average_count",  // Normalize the data by finding the average for each day of the week
					when(col("day_of_week").isInCollection(PatternDetector.minDaysSeq),  // Find average for minimum counted days
						// round(col("count") / PatternDetector.minCount).cast(LongType))  // *Note: The average is rounded and then cast to LongType
						col("count") / PatternDetector.minCount)  // Not rounded
					.otherwise(  // Find average for maximum counted days
						// round(col("count") / PatternDetector.maxCount).cast(LongType)))  // *Note: The average is rounded and then cast to LongType
						col("count") / PatternDetector.maxCount))  // Not rounded
			.withColumn("average_total",  // Normalize the data by finding the average for each day of the week
					when(col("day_of_week").isInCollection(PatternDetector.minDaysSeq),  // Find average for minimum total days
						// round(col("total") / PatternDetector.minCount).cast(LongType))  // *Note: The average is rounded and then cast to LongType
						col("total") / PatternDetector.minCount)  // Not rounded
					.otherwise(  // Find average for maximum counted days
						col("total") / PatternDetector.maxCount))  // Not rounded
			.drop("count", "total")  // Drop the un-normalized data
		var newDfSucc = data  // Generate the "average_total_successful" data
			.select(col("country").as("temp_country"), col("datetime"), col("qty"))
			.where("payment_txn_success = 'Y'")
			.withColumn("temp_day_of_week", date_format(col("datetime"), "EEEE"))  // Create a column with the day of week name for each date
			.groupBy("temp_country", "temp_day_of_week")
			.agg(sum("qty").as("total"))
			.withColumn("average_total_successful",  // Normalize the data by finding the average for each day of the week
					when(col("temp_day_of_week").isInCollection(PatternDetector.minDaysSeq),  // Find average for minimum total days
						// round(col("total") / PatternDetector.minCount).cast(LongType))  // *Note: The average is rounded and then cast to LongType
						col("total") / PatternDetector.minCount)  // Not rounded
					.otherwise(  // Find average for maximum counted days
						// round(col("total") / PatternDetector.maxCount).cast(LongType)))  // *Note: The average is rounded and then cast to LongType
						col("total") / PatternDetector.maxCount))  // Not rounded
			.withColumn("daynum_of_week", coalesce(PatternDetector.daymapCol(col("temp_day_of_week")), lit("")))  // Create a column to order by
			.drop("total")  // Drop the un-normalized data
		newDf = newDf  // Merge the two dataframes
			.join(newDfSucc, newDf("country") === newDfSucc("temp_country") && newDf("day_of_week") === newDfSucc("temp_day_of_week"), "full")
			.drop("temp_country", "temp_day_of_week")
			.orderBy(col("country"), col("daynum_of_week"))
		if (PatternDetector.testMode)  // If we're in test mode...
			newDf.show(false)  // ...show the data
		// val ndev = PatternDetector.deviation2F(newDf)  // Check the data for a pattern
		val ndev = PatternDetector.getDeviationDouble(newDf, 2, Seq(0, 1))  // Check the data for a pattern
		var filename = ""
		if (ndev > 1.0 + PatternDetector.marginOfError) {  // Pattern detected
			filename = PatternDetector.saveDataFrameAsCSV(newDf, "CountryByDayOfWeekRates.csv")  // Write the data to a file
			if (ndev < 2)
				Option("Found possible pattern (" + ((ndev - 1) * 100) + "% chance)\nFilename: " + filename)
			else
				Option("Found pattern (100% chance)\nFilename: " + filename)
		} else {  // No pattern detected
			if (PatternDetector.forceCSV) {
				filename = PatternDetector.saveDataFrameAsCSV(newDf, "CountryByDayOfWeekRates.csv")  // Write the data to a file
				if (PatternDetector.testMode)  // If we're in test mode...
					println(s"Data force-saved as: $filename\n")  // ...show the filename
			}
			None
		}
	}
}
