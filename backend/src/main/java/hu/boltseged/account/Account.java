package hu.boltseged.account;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="accounts")
public class Account {
  @Id private UUID id; private String companyName; private String taxNumber; private String billingAddress;
  private String contactName; private String email; private String passwordHash; private String role;
  private String senderCountryCode; private String senderPostalCode; private String senderCityName;
  private String senderAddressLine1; private String senderAddressLine2; private String senderStateOrProvinceCode; private String senderPhone;
  private String pickupCompanyName; private String pickupContactName; private String pickupCountryCode;
  private String pickupPostalCode; private String pickupCityName; private String pickupAddressLine1; private String pickupAddressLine2;
  private String pickupStateOrProvinceCode; private String pickupPhone; private String pickupEmail;
  private boolean active = true; private String pricingType; private BigDecimal pricingValue;
  private Instant createdAt; private Instant updatedAt;
  protected Account() {}
  public Account(String companyName,String email,String passwordHash,String role){this.id=UUID.randomUUID();this.companyName=companyName;this.email=email;this.passwordHash=passwordHash;this.role=role;this.pricingType="CUSTOMER".equals(role)?"FIXED":"PERCENTAGE";this.pricingValue="CUSTOMER".equals(role)?new BigDecimal("1000"):BigDecimal.ZERO;this.createdAt=Instant.now();this.updatedAt=this.createdAt;}
  public UUID getId(){return id;} public String getCompanyName(){return companyName;} public String getTaxNumber(){return taxNumber;} public String getBillingAddress(){return billingAddress;} public String getContactName(){return contactName;} public String getEmail(){return email;} public String getPasswordHash(){return passwordHash;} public String getRole(){return role;} public boolean isActive(){return active;} public void setActive(boolean v){active=v;} public String getPricingType(){return pricingType;} public BigDecimal getPricingValue(){return pricingValue;}
  public String getSenderCountryCode(){return senderCountryCode;} public String getSenderPostalCode(){return senderPostalCode;} public String getSenderCityName(){return senderCityName;} public String getSenderAddressLine1(){return senderAddressLine1;} public String getSenderAddressLine2(){return senderAddressLine2;} public String getSenderStateOrProvinceCode(){return senderStateOrProvinceCode;} public String getSenderPhone(){return senderPhone;}
  public String getPickupCompanyName(){return pickupCompanyName;} public String getPickupContactName(){return pickupContactName;} public String getPickupCountryCode(){return pickupCountryCode;} public String getPickupPostalCode(){return pickupPostalCode;} public String getPickupCityName(){return pickupCityName;} public String getPickupAddressLine1(){return pickupAddressLine1;} public String getPickupAddressLine2(){return pickupAddressLine2;} public String getPickupStateOrProvinceCode(){return pickupStateOrProvinceCode;} public String getPickupPhone(){return pickupPhone;} public String getPickupEmail(){return pickupEmail;}
  public void update(String company,String tax,String billing,String contact,String mail,String type,BigDecimal value){companyName=company;taxNumber=tax;billingAddress=billing;contactName=contact;email=mail;pricingType=type;pricingValue=value;updatedAt=Instant.now();}
  public void updateCompanyProfile(String company,String tax,String billing,String contact){companyName=company;taxNumber=tax;billingAddress=billing;contactName=contact;updatedAt=Instant.now();}
  public void updateCompanyProfile(String company,String tax,String billing,String contact,String country,String postal,String city,String line1,String line2,String state,String phone,String pickupCompany,String pickupContact,String pickupCountry,String pickupPostal,String pickupCity,String pickupLine1,String pickupLine2,String pickupState,String pickupPhoneValue,String pickupEmailValue){companyName=company;taxNumber=tax;billingAddress=billing;contactName=contact;senderCountryCode=country;senderPostalCode=postal;senderCityName=city;senderAddressLine1=line1;senderAddressLine2=line2;senderStateOrProvinceCode=state;senderPhone=phone;pickupCompanyName=pickupCompany;pickupContactName=pickupContact;pickupCountryCode=pickupCountry;pickupPostalCode=pickupPostal;pickupCityName=pickupCity;pickupAddressLine1=pickupLine1;pickupAddressLine2=pickupLine2;pickupStateOrProvinceCode=pickupState;pickupPhone=pickupPhoneValue;pickupEmail=pickupEmailValue;updatedAt=Instant.now();}
  public void setPasswordHash(String value){passwordHash=value;updatedAt=Instant.now();}
}
