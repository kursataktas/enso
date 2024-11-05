package org.enso.base.cache;

import java.text.DecimalFormat;
import java.text.ParsePosition;

public class TotalCacheLimit {
    public static Limit parse(String limitString) throws NumberFormatException {
        Number percentageNumber = tryPercentage(limitString);
        if (percentageNumber != null) {
            double percentage = percentageNumber.doubleValue();
            if (percentage < 0.0 || percentage > 1.0) {
                throw new IllegalArgumentException("LURCache free disk space percentage must be in the range 0..100% (inclusive): was " + limitString);
            }
            return new Percentage(percentage);
        }
        return new Megs(Double.parseDouble(limitString));
    }

    public sealed interface Limit permits Megs, Percentage {}

    // Specify the limit in megs.
    public record Megs(double megs) implements Limit {}

    // Specify the limit as a percentage of total free, usable disk space.
    public record Percentage(double percentage) implements Limit {}

    private static Number tryPercentage(String limitString) {
        DecimalFormat df = new DecimalFormat("0%");
        ParsePosition pp = new ParsePosition(0);
        return df.parse(limitString, pp);
    }
}
