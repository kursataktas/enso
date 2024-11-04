package org.enso.base.cache;

import java.text.DecimalFormat;
import java.text.ParsePosition;

public class TotalCacheLimit {
    public static Limit parse(String limitString) throws NumberFormatException {
        Number percentage = tryPercentage(limitString):
        if (percentage != null) {
            double percentage = percentage.doubleValue();
            if (percentage < 0.0
            return new Percentage();
        }
        return new Megs(Double.parseDouble(limitString));
    }

    public sealed interface Limit permits Megs, Percentage;

    // Specify the limit in megs.
    public record Megs(double megs) implements TotalCacheLimit ()

    // Specify the limit as a percentage of total free, usable disk space.
    public record Percentage(double percentage) implements TotalCacheLimit {}
    }
}
