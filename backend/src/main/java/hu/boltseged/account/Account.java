package hu.boltseged.account;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="accounts")
public class Account {
  @Id private UUID id; private String companyName; private String taxNumber; private String billingAddress;
  private String contactName; private String email; private String passwordHash; private String role;
  private boolean active = true; private String pricingType; private BigDecimal pricingValue;
  private Instant createdAt; private Instant updatedAt;
  protected Account() {}
  public Account(String companyName,String email,String passwordHash,String role){this.id=UUID.randomUUID();this.companyName=companyName;this.email=email;this.passwordHash=passwordHash;this.role=role;this.pricingType="PERCENTAGE";this.pricingValue=BigDecimal.ZERO;this.createdAt=Instant.now();this.updatedAt=this.createdAt;}
  public UUID getId(){return id;} public String getCompanyName(){return companyName;} public String getTaxNumber(){return taxNumber;} public String getBillingAddress(){return billingAddress;} public String getContactName(){return contactName;} public String getEmail(){return email;} public String getPasswordHash(){return passwordHash;} public String getRole(){return role;} public boolean isActive(){return active;} public void setActive(boolean v){active=v;} public String getPricingType(){return pricingType;} public BigDecimal getPricingValue(){return pricingValue;} public void update(String company,String tax,String billing,String contact,String mail,String type,BigDecimal value){companyName=company;taxNumber=tax;billingAddress=billing;contactName=contact;email=mail;pricingType=type;pricingValue=value;updatedAt=Instant.now();} public void updateCompanyProfile(String company,String tax,String billing,String contact){companyName=company;taxNumber=tax;billingAddress=billing;contactName=contact;updatedAt=Instant.now();} public void setPasswordHash(String value){passwordHash=value;updatedAt=Instant.now();}
}
