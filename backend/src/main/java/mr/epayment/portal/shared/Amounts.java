package mr.epayment.portal.shared;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

/** Montants à la française : 1 234 567,89. */
public final class Amounts {

    private Amounts() {
    }

    public static String format(BigDecimal value) {
        var symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.00", symbols).format(value);
    }
}
