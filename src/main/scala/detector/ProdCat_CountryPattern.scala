package detector

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions._

object ProdCat_CountryPattern {

	/**
	  * Tests for a pattern in the purchase frequency by product category and country.
	  *
	  * @param data	Dataframe to search for a pattern on.
	  * @return		Search result as `Option[String]`.  (`None` = no pattern)
	  */
	def Go(data: DataFrame): Option[String] = {
		var newDf = data  // Generate the "count" and "total" data
			.select("product_category", "country", "qty")
			.groupBy("product_category", "country")
			.agg(count("product_category").as("count"), sum("qty").as("total"))
		var newDfSucc = data  // Generate the "total_successful" data
			.select(col("product_category").as("temp_product_category"), col("country").as("temp_country"), col("qty"))
			.where("payment_txn_success = 'Y'")
			.groupBy("temp_product_category", "temp_country")
			.agg(sum("qty").as("total_successful"))
		newDf = newDf  // Merge the two dataframes
			.join(newDfSucc, newDf("product_category") === newDfSucc("temp_product_category") && newDf("country") === newDfSucc("temp_country"), "full")
			.drop("temp_product_category", "temp_country")
			.orderBy("product_category", "country")
		if (PatternDetector.testMode)  // If we're in test mode...
			newDf.show(false)  // ...show the data
		val ndev = PatternDetector.deviation2F(newDf)  // Check the data for a pattern
		var filename = ""
		if (ndev > 1.0 + PatternDetector.marginOfError) {  // Pattern detected
			val filename = PatternDetector.saveDataFrameAsCSV(newDf, "ProdCat_CountryRates.csv")  // Write the data to a file
			if (ndev < 2)
				Option("Found possible pattern (" + ((ndev - 1) * 100) + "% chance)\nFilename: " + filename)
			else
				Option("Found pattern (100% chance)\nFilename: " + filename)
		} else {  // No pattern detected
			if (PatternDetector.forceCSV) {
				filename = PatternDetector.saveDataFrameAsCSV(newDf, "ProdCat_CountryRates.csv")  // Write the data to a file
				if (PatternDetector.testMode)  // If we're in test mode...
					println(s"Data force-saved as: $filename\n")  // ...show the filename
			}
			None
		}
	}
}
