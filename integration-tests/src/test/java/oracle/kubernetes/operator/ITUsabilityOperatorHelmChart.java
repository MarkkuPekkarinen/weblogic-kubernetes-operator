// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.Map;
import oracle.kubernetes.operator.utils.ExecCommand;
import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.Operator.RESTCertType;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * Simple JUnit test file used for testing Operator.
 *
 * <p>This test is used for testing Helm install for Operator(s)
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ITUsabilityOperatorHelmChart extends BaseTest {

  private static int number = 3;
  String oprelease = "op" + number;

  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes.
   *
   * @throws Exception
   */
  @BeforeClass
  public static void staticPrepare() throws Exception {
    if (!QUICKTEST) {
      // initialize test properties and create the directories
      initialize(APP_PROPS_FILE);
    }
  }

  /**
   * Releases k8s cluster lease, archives result, pv directories
   *
   * @throws Exception
   */
  @AfterClass
  public static void staticUnPrepare() throws Exception {
    if (!QUICKTEST) {
      logger.info("+++++++++++++++++++++++++++++++++---------------------------------+");
      logger.info("BEGIN");
      logger.info("Run once, release cluster lease");

      StringBuffer cmd =
          new StringBuffer("export RESULT_ROOT=$RESULT_ROOT && export PV_ROOT=$PV_ROOT && ");
      cmd.append(BaseTest.getProjectRoot())
          .append("/integration-tests/src/test/resources/statedump.sh");
      logger.info("Running " + cmd);

      ExecResult result = ExecCommand.exec(cmd.toString());
      if (result.exitValue() == 0) logger.info("Executed statedump.sh " + result.stdout());
      else
        logger.info(
            "Execution of statedump.sh failed, " + result.stderr() + "\n" + result.stdout());

      if (JENKINS) {
        cleanup();
      }

      if (getLeaseId() != "") {
        logger.info("Release the k8s cluster lease");
        TestUtils.releaseLease(getProjectRoot(), getLeaseId());
      }

      logger.info("SUCCESS");
    }
  }

  /**
   * Helm will install 2 operators, delete, install again second operator with same attributes
   *
   * @throws Exception
   */
  @Test
  public void testOperatorCreateDeleteCreate() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    Operator firstoperator = null;
    Operator secondoperator = null;
    try {
      logger.info("Checking if first operator is running, if not creating");
      firstoperator =
          new Operator(TestUtils.createOperatorMap(number, true), RESTCertType.SELF_SIGNED);
      firstoperator.callHelmInstall();
      number = number + 1;
      oprelease = "op" + number;
      logger.info(" new value for oprelease " + oprelease);
      secondoperator =
          new Operator((TestUtils.createOperatorMap(number, true)), RESTCertType.SELF_SIGNED);
      secondoperator.callHelmInstall();

      logger.info("Delete second operator and verify the first operator pod still running");
      secondoperator.destroy();
      Thread.sleep(BaseTest.getWaitTimePod() * 1000);

      logger.info("Verify first perator pod is running");
      firstoperator.verifyOperatorReady();
      logger.info(
          "Create again second operator pod and verify it is started again after create - delete -create steps");
      secondoperator =
          new Operator(
              (TestUtils.createOperatorMap(number, true)),
              false,
              false,
              false,
              RESTCertType.SELF_SIGNED);
      secondoperator.callHelmInstall();

    } finally {
      number++;
    }
    if (firstoperator != null) {
      firstoperator.destroy();
    }
    if (secondoperator != null) {
      secondoperator.destroy();
    }
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Negative test: Helm will install 2 operators, with same namespace, second operator should fail
   *
   * @throws Exception
   */
  @Test
  public void testCreateSecondOperatorUsingSameOperatorNSNegativeInstall() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    Operator firstoperator = null;
    Operator secondoperator = null;

    try {
      logger.info("Creating firs toperator");
      firstoperator =
          new Operator(TestUtils.createOperatorMap(number, true), RESTCertType.SELF_SIGNED);
      firstoperator.callHelmInstall();
      number = number + 1;
      oprelease = "op" + number;
      logger.info(" new value for oprelease" + oprelease);
      Map<String, Object> operatorMap = TestUtils.createOperatorMap(number, true);
      operatorMap.replace("namespace", firstoperator.getOperatorMap().get("namespace"));
      secondoperator = new Operator(operatorMap, false, true, true, RESTCertType.SELF_SIGNED);
      secondoperator.callHelmInstall();
      throw new RuntimeException(
          "FAILURE: Helm installs second operator with same namespace as the first one");

    } catch (Exception ex) {
      if (!ex.getMessage()
          .contains(
              "Error: release "
                  + oprelease
                  + " failed: secrets \"weblogic-operator-secrets\" already exists")) {
        throw new RuntimeException(
            "FAILURE: Helm installs second operator with same namespace as the first one does not report expected message "
                + ex.getMessage());
      }
      String cmdLb = "helm list --failed " + "  | grep " + oprelease;
      logger.info("Executing cmd " + cmdLb);
      ExecResult result = ExecCommand.exec(cmdLb);
      if (result.exitValue() != 0) {
        throw new RuntimeException(
            "FAILURE: Helm installs second operator with same namespace as the first one ");
      }
    } finally {
      number++;
      if (firstoperator != null) {
        firstoperator.destroy();
      }
      if (secondoperator != null) {
        secondoperator.destroy();
      }
    }
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Negative test : Helm will install the operator with not preexisted operator namespace
   *
   * @throws Exception
   */
  @Test
  public void testNotPreCreatedOpNSCreateOperatorNegativeInstall() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    Operator operator = null;
    try {
      operator =
          new Operator(
              (TestUtils.createOperatorMap(number, false)), false, false, true, RESTCertType.NONE);
      operator.callHelmInstall();
      throw new RuntimeException("FAILURE: Helm install operator with not preexisted namespace ");

    } catch (Exception ex) {
      String cmdLb = "helm list --failed " + "  | grep " + oprelease;
      logger.info("Executing cmd " + cmdLb);
      ExecResult result = ExecCommand.exec(cmdLb);
      if (result.exitValue() != 0) {
        throw new RuntimeException(
            "FAILURE: Helm installs operator with not preexisted namespace ");
      }
    } finally {
      number++;
      if (operator != null) {
        operator.destroy();
      }
    }
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Negative test : Helm will install the operator with not preexisted operator service account,
   * deployment will not start until service account will be created
   *
   * @throws Exception
   */
  @Test
  public void testNotPreexistedOpServiceAccountCreateOperatorNegativeInstall() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    Operator operator = null;
    try {
      operator =
          new Operator(
              (TestUtils.createOperatorMap(number, false)), true, false, true, RESTCertType.NONE);
      operator.callHelmInstall();
      throw new RuntimeException(
          "FAILURE: Helm installs operator with not preexisted service account ");

    } catch (Exception ex) {
      String cmdLb = "helm list --failed " + "  | grep " + oprelease;
      logger.info("Executing cmd " + cmdLb);
      ExecResult result = ExecCommand.exec(cmdLb);
      if (result.exitValue() != 0) {
        throw new RuntimeException(
            "FAILURE: Helm installs operator with not preexisted service account ");
      }
      // create operator service account
      String serviceAccount = (String) operator.getOperatorMap().get("serviceAccount");
      String operatorNS = (String) operator.getOperatorMap().get("namespace");
      if (serviceAccount != null && !serviceAccount.equals("default")) {
        result =
            ExecCommand.exec(
                "kubectl create serviceaccount " + serviceAccount + " -n " + operatorNS);
        if (result.exitValue() != 0) {
          throw new RuntimeException(
              "FAILURE: Couldn't create serviceaccount "
                  + serviceAccount
                  + ". Cmd returned "
                  + result.stdout()
                  + "\n"
                  + result.stderr());
        }
      }
      // after service account created the operator should be started
      Thread.sleep(BaseTest.getWaitTimePod() * 2000);
      operator.verifyOperatorReady();
    } finally {
      number++;
      if (operator != null) {
        operator.destroy();
      }
    }
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Negative test : Helm will install the second operator with same target domain namespace as the
   * first operator
   *
   * @throws Exception
   */
  @Test
  public void testSecondOpSharingSameTargetDomainsNSNegativeInstall() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    Operator secondoperator = null;
    Operator firstoperator = null;
    try {
      logger.info("Creating first operator");
      firstoperator =
          new Operator(TestUtils.createOperatorMap(number, true), RESTCertType.SELF_SIGNED);
      firstoperator.callHelmInstall();
      number = number + 1;
      oprelease = "op" + number;
      Map<String, Object> operatorMap = TestUtils.createOperatorMap(number, false);
      ArrayList<String> targetDomainsNS =
          (ArrayList<String>) firstoperator.getOperatorMap().get("domainNamespaces");
      operatorMap.put("domainNamespaces", targetDomainsNS);
      secondoperator = new Operator(operatorMap, true, true, false, RESTCertType.NONE);
      secondoperator.callHelmInstall();
      throw new RuntimeException(
          "FAILURE: Helm installs second operator with same as first operator's target domains namespaces ");

    } catch (Exception ex) {
      logger.info("Caught exception " + ex.getMessage() + ex.getStackTrace());
      if (!ex.getMessage()
          .contains(
              "Error: release "
                  + oprelease
                  + " failed: rolebindings.rbac.authorization.k8s.io \"weblogic-operator-rolebinding-namespace\" already exists")) {
        throw new RuntimeException(
            "FAILURE: Helm installs second operator with same as first operator's target domains namespaces does not report expected message "
                + ex.getMessage());
      }
      ;
      String cmdLb = "helm list --failed " + "  | grep " + oprelease;
      logger.info("Executing cmd " + cmdLb);
      ExecResult result = ExecCommand.exec(cmdLb);
      if (result.exitValue() != 0) {
        throw new RuntimeException(
            "FAILURE: Helm installs second operator with same as first operator's target domains namespaces ");
      }

    } finally {
      number++;
      if (firstoperator != null) {
        firstoperator.destroy();
      }
      if (secondoperator != null) {
        secondoperator.destroy();
      }
    }
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Negative test : Create operator with not preexisted target domain namespace
   *
   * @throws Exception
   */
  @Test
  public void testTargetNSIsNotPreexistedNegativeInstall() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    Operator operator = null;
    try {
      operator =
          new Operator(
              TestUtils.createOperatorMap(number, false), true, true, false, RESTCertType.NONE);
      operator.callHelmInstall();
      throw new RuntimeException(
          "FAILURE: Helm install operator with not preexisted target domains namespaces ");

    } catch (Exception ex) {
      if (!ex.getMessage()
          .contains(
              "Error: release "
                  + oprelease
                  + " failed: namespaces \"test"
                  + number
                  + "\" not found")) {
        throw new RuntimeException(
            "FAILURE: Helm install operator with not preexisted target domains namespaces does not report expected message "
                + ex.getMessage());
      }
      ;
      String cmdLb = "helm list --failed " + "  | grep " + oprelease;
      logger.info("Executing cmd " + cmdLb);
      ExecResult result = ExecCommand.exec(cmdLb);
      if (result.exitValue() != 0) {
        throw new RuntimeException(
            "FAILURE: Helm install operator with not preexisted target domains namespaces ");
      }

    } finally {
      number++;
      if (operator != null) {
        operator.destroy();
      }
    }
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Negative test : Helm installs the second operator with same ExternalRestPort as the first
   * operator
   *
   * @throws Exception
   */
  @Test
  public void testSecondOpSharingSameExternalRestPortNegativeInstall() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    Operator operator1 = null;
    Operator operator2 = null;
    int httpsRestPort = 0;
    try {
      operator1 = new Operator(TestUtils.createOperatorMap(number, true), RESTCertType.SELF_SIGNED);
      operator1.callHelmInstall();

      httpsRestPort = (int) operator1.getOperatorMap().get("externalRestHttpsPort");
      logger.info("Creating second operator with externalRestHttpPort " + httpsRestPort);
      number = number + 1;
      oprelease = "op" + number;
      Map<String, Object> operatorMap = TestUtils.createOperatorMap(number, true);
      operatorMap.replace("externalRestHttpsPort", httpsRestPort);

      operator2 = new Operator(operatorMap, RESTCertType.SELF_SIGNED);
      operator2.callHelmInstall();

      throw new RuntimeException(
          "FAILURE: Helm install operator with dublicated Rest Port number ");

    } catch (Exception ex) {
      logger.info("Error message " + ex.getMessage());
      if (!ex.getMessage()
          .contains(
              "Service \"external-weblogic-operator-svc\" is invalid: spec.ports[0].nodePort: Invalid value:")) {
        throw new RuntimeException(
            "FAILURE: Helm install operator with dublicated rest port number does not report expected message "
                + ex.getMessage());
      }
      ;
      String cmdLb = "helm list --failed " + "  | grep " + oprelease;
      logger.info("Executing cmd " + cmdLb);
      ExecResult result = ExecCommand.exec(cmdLb);
      if (result.exitValue() != 0) {
        throw new RuntimeException(
            "FAILURE: Helm install operator with dublicated Rest Port number ");
      }

    } finally {
      number++;
      if (operator1 != null) {
        operator1.destroy();
      }
      if (operator2 != null) {
        operator2.destroy();
      }
    }
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Negative test : Helm installs the operator with invalid target domains namespaces (UpperCase)
   *
   * @throws Exception
   */
  @Test
  public void testCreateWithUpperCaseTargetDomainNegativeInstall() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    Operator operator = null;
    try {
      Map<String, Object> operatorMap = TestUtils.createOperatorMap(number, true);
      ArrayList<String> targetDomainsNS = new ArrayList<String>();
      targetDomainsNS.add("Test9");
      operatorMap.replace("domainNamespaces", targetDomainsNS);
      operator = new Operator(operatorMap, RESTCertType.SELF_SIGNED);
      operator.callHelmInstall();
      throw new RuntimeException(
          "FAILURE: Helm install operator with UpperCase for target domains ");

    } catch (Exception ex) {
      if (!ex.getMessage()
          .contains("Error: release " + oprelease + " failed: namespaces \"Test9\" not found")) {
        throw new RuntimeException(
            "FAILURE: Helm installs the operator with UpperCase for target domains namespace does not report expected message "
                + ex.getMessage());
      }
      ;
      String cmdLb = "helm list --failed " + "  | grep " + oprelease;
      logger.info("Executing cmd " + cmdLb);
      ExecResult result = ExecCommand.exec(cmdLb);
      if (result.exitValue() != 0) {
        throw new RuntimeException(
            "FAILURE: Helm installs the operator with UpperCase Target Domain NS ");
      }

    } finally {
      number++;
      if (operator != null) {
        operator.destroy();
      }
    }
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Negative test : Helm installs the operator with invalid attributes values
   *
   * @throws Exception
   */
  @Test
  public void testCreateChartWithInvalidAttributesNegativeInstall() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    Operator operator = null;
    Map<String, Object> operatorMap = TestUtils.createOperatorMap(number, true);

    try {
      operatorMap.put("elkIntegrationEnabled", "true");
      operator = new Operator(operatorMap, RESTCertType.SELF_SIGNED);
      operator.callHelmInstall();
      throw new RuntimeException(
          "FAILURE: Helm installs the operator with invalid value for attribute elkIntegrationEnabled ");

    } catch (Exception ex) {
      if (!ex.getMessage().contains("elkIntegrationEnabled must be a bool : string")) {
        throw new RuntimeException(
            "FAILURE: Helm installs the operator with invalid value for attribute elkIntegrationEnabled does not report expected message "
                + ex.getMessage());
      }
      ;
    }
    try {
      operatorMap = TestUtils.createOperatorMap(number, true);

      operatorMap.put("javaLoggingLevel", "INVALIDOPTION");
      operator = new Operator(operatorMap, false, false, false, RESTCertType.SELF_SIGNED);
      operator.callHelmInstall();
      throw new RuntimeException(
          "FAILURE: Helm installs the operator with invalid value for attribute javaLoggingLevel ");

    } catch (Exception ex) {
      if (!ex.getMessage()
          .contains(
              "javaLoggingLevel must be one of the following values [SEVERE WARNING INFO CONFIG FINE FINER FINEST] : INVALIDOPTION")) {
        throw new RuntimeException(
            "FAILURE: Helm installs the operator with invalid value for attribute externalRestEnabled does not report expected message "
                + ex.getMessage());
      }
    } finally {
      number++;
    }
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Helm will install the operator with empty target domains namespaces
   *
   * @throws Exception
   */
  @Test
  public void testCreateWithEmptyTargetDomainInstall() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    Operator operator = null;
    try {
      Map<String, Object> operatorMap = TestUtils.createOperatorMap(number, true);
      ArrayList<String> targetDomainsNS = new ArrayList<String>();
      operatorMap.replace("domainNamespaces", targetDomainsNS);
      operator = new Operator(operatorMap, RESTCertType.SELF_SIGNED);
      operator.callHelmInstall();
      operator.verifyOperatorReady();

    } finally {
      number++;
      if (operator != null) {
        operator.destroy();
      }
    }
    logger.info("SUCCESS - " + testMethodName);
  }

  private void upgradeOperator(Operator operator, String upgradeSet) throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    operator.callHelmUpgrade(upgradeSet);
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Helm installs the operator with default target domains namespaces
   *
   * @throws Exception
   */
  @Test
  public void testCreateWithDefaultTargetDomainInstall() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    Operator operator = null;
    try {
      Map<String, Object> operatorMap = TestUtils.createOperatorMap(number, true);
      ArrayList<String> targetDomainsNS = new ArrayList<String>();
      targetDomainsNS.add("default");
      operatorMap.replace("domainNamespaces", targetDomainsNS);
      operator = new Operator(operatorMap, true, true, false, RESTCertType.SELF_SIGNED);
      operator.callHelmInstall();
      operator.verifyOperatorReady();

    } finally {
      number++;
      if (operator != null) {
        operator.destroy();
      }
    }
    logger.info("SUCCESS - " + testMethodName);
  }
}
