// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import javax.annotation.Nonnull;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class ManagedServer extends Server {
  /** The name of the managed server. Required. */
  @SerializedName("serverName")
  @Expose
  private String serverName;

  public String getServerName() {
    return serverName;
  }

  public void setServerName(@Nonnull String serverName) {
    this.serverName = serverName;
  }

  ManagedServer withServerName(@Nonnull String serverName) {
    setServerName(serverName);
    return this;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .appendSuper(super.toString())
        .append("serverName", serverName)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;

    if (o == null || getClass() != o.getClass()) return false;

    if (!(o instanceof ManagedServer)) return false;

    ManagedServer that = (ManagedServer) o;

    return new EqualsBuilder()
        .appendSuper(super.equals(o))
        .append(serverName, that.serverName)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .appendSuper(super.hashCode())
        .append(serverName)
        .toHashCode();
  }
}
