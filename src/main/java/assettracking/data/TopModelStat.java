package assettracking.data;

/**
 * A simple record to hold statistics for the 'Top Processed Models' table.
 * Using a record is the most modern and concise way to represent this data.
 * It automatically provides accessor methods like 'modelNumber()' and 'count()'.
 */
public record TopModelStat(String modelNumber, int count) {
}