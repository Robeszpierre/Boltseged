package hu.boltseged.billing;
import hu.boltseged.account.Account; import java.math.*;
public final class PricingCalculator { private PricingCalculator(){} public static BigDecimal customerPrice(Account a,BigDecimal cost){BigDecimal v=a.getPricingValue()==null?BigDecimal.ZERO:a.getPricingValue();BigDecimal result="FIXED".equalsIgnoreCase(a.getPricingType())?cost.add(v):cost.add(cost.multiply(v).divide(BigDecimal.valueOf(100),2,RoundingMode.HALF_UP));return result.setScale(2,RoundingMode.HALF_UP);} }
