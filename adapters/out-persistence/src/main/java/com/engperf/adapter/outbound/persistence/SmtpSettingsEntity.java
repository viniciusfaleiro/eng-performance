package com.engperf.adapter.outbound.persistence;

import com.engperf.domain.config.MailTransport;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA mapping for the singleton outgoing-email configuration (id = "default"). The password is
 * stored only as ciphertext ({@link #passwordCiphertext}) — encryption/decryption happens in the
 * repository adapter, never here and never above this module.
 */
@Entity
@Table(name = "smtp_settings")
public class SmtpSettingsEntity {

  @Id private String id;

  @Column(nullable = false)
  private boolean enabled;

  private String host;
  private Integer port;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private MailTransport transport;

  private String username;

  @Column(name = "password_ciphertext")
  private String passwordCiphertext;

  @Column(name = "from_address")
  private String fromAddress;

  @Column(name = "from_name")
  private String fromName;

  @Column(name = "app_base_url")
  private String appBaseUrl;

  protected SmtpSettingsEntity() {}

  /**
   * Ten constructor parameters would trip Checkstyle's {@code ParameterNumber} — built via setters
   * instead.
   */
  public SmtpSettingsEntity(String id) {
    this.id = id;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public Integer getPort() {
    return port;
  }

  public void setPort(Integer port) {
    this.port = port;
  }

  public MailTransport getTransport() {
    return transport;
  }

  public void setTransport(MailTransport transport) {
    this.transport = transport;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPasswordCiphertext() {
    return passwordCiphertext;
  }

  public void setPasswordCiphertext(String passwordCiphertext) {
    this.passwordCiphertext = passwordCiphertext;
  }

  public String getFromAddress() {
    return fromAddress;
  }

  public void setFromAddress(String fromAddress) {
    this.fromAddress = fromAddress;
  }

  public String getFromName() {
    return fromName;
  }

  public void setFromName(String fromName) {
    this.fromName = fromName;
  }

  public String getAppBaseUrl() {
    return appBaseUrl;
  }

  public void setAppBaseUrl(String appBaseUrl) {
    this.appBaseUrl = appBaseUrl;
  }
}
