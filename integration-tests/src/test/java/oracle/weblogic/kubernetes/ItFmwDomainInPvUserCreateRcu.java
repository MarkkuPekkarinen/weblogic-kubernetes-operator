// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.domain.DomainCreationImage;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.WitParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DB_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_HOST;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.MII_AUXILIARY_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePod;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorPodName;
import static oracle.weblogic.kubernetes.utils.AuxiliaryImageUtils.createAndPushAuxiliaryImage;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.addSccToDBSvcAccount;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getUniqueName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.verifyConfiguredSystemResource;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.createConfigMapAndVerify;
import static oracle.weblogic.kubernetes.utils.DbUtils.createRcuAccessSecret;
import static oracle.weblogic.kubernetes.utils.DbUtils.createRcuSchema;
import static oracle.weblogic.kubernetes.utils.DbUtils.startOracleDB;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.DomainUtils.deleteDomainResource;
import static oracle.weblogic.kubernetes.utils.FmwUtils.createDomainResourceSimplifyJrfPv;
import static oracle.weblogic.kubernetes.utils.FmwUtils.restoreOpssWalletfileSecret;
import static oracle.weblogic.kubernetes.utils.FmwUtils.saveAndRestoreOpssWalletfileSecret;
import static oracle.weblogic.kubernetes.utils.FmwUtils.verifyDomainReady;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodLogContains;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createOpsswalletFileSecretWithoutFile;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createOpsswalletpasswordSecret;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.apache.commons.io.FileUtils.delete;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test to create a FMW domain on PV with DomainOnPvSimplification feature when user pre-creates RCU.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test for initializeDomainOnPV when user per-creates RCU")
@IntegrationTest
@Tag("kind-sequential")
public class ItFmwDomainInPvUserCreateRcu {

  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static String dbNamespace = null;

  private static final String RCUSCHEMAPREFIX = "jrfdomainpv";
  private static final String ORACLEDBURLPREFIX = "oracledb.";
  private static String ORACLEDBSUFFIX = null;
  private static final String RCUSCHEMAPASSWORD = "Oradoc_db1";

  private static String dbUrl = null;
  private static LoggingFacade logger = null;
  private static String DOMAINHOMEPREFIX = null;
  private static final String domainUid1 = "jrfdomainonpv-userrcu1";
  private static final String domainUid4 = "jrfdomainonpv-userrcu4";
  private static final String miiAuxiliaryImage1Tag = "jrf1" + MII_BASIC_IMAGE_TAG;
  private final String adminSecretName1 = domainUid1 + "-weblogic-credentials";
  private final String rcuaccessSecretName1 = domainUid1 + "-rcu-credentials";
  private final String opsswalletpassSecretName1 = domainUid1 + "-opss-wallet-password-secret";
  private final String opsswalletfileSecretName1 = domainUid1 + "-opss-wallet-file-secret";
  private final String adminSecretName4 = domainUid4 + "-weblogic-credentials";
  private final String rcuaccessSecretName4 = domainUid4 + "-rcu-credentials";
  private final String opsswalletpassSecretName4 = domainUid4 + "-opss-wallet-password-secret";
  private final String opsswalletfileSecretName4 = domainUid4 + "-opss-wallet-file-secret";
  private static final int replicaCount = 1;

  private final String fmwModelFilePrefix = "model-fmwdomainonpv-rcu-wdt";
  private final String fmwModelFile = fmwModelFilePrefix + ".yaml";
  private static DomainCreationImage domainCreationImage1 = null;
  private static List<DomainCreationImage> domainCreationImages4 = new ArrayList<>();
  private static String configMapName = null;

  /**
   * Assigns unique namespaces for DB, operator and domain.
   * Start DB service and create RCU schema.
   * Pull FMW image and Oracle DB image if running tests in Kind cluster.
   */
  @BeforeAll
  public static void initAll(@Namespaces(3) List<String> namespaces) {
    logger = getLogger();

    // get a new unique dbNamespace
    logger.info("Assign a unique namespace for DB and RCU");
    assertNotNull(namespaces.get(0), "Namespace is null");
    dbNamespace = namespaces.get(0);
    final int dbListenerPort = getNextFreePort();
    ORACLEDBSUFFIX = ".svc.cluster.local:" + dbListenerPort + "/devpdb.k8s";
    dbUrl = ORACLEDBURLPREFIX + dbNamespace + ORACLEDBSUFFIX;

    // get a new unique opNamespace
    logger.info("Assign a unique namespace for operator1");
    assertNotNull(namespaces.get(1), "Namespace is null");
    opNamespace = namespaces.get(1);

    // get a new unique domainNamespace
    logger.info("Assign a unique namespace for FMW domain");
    assertNotNull(namespaces.get(2), "Namespace is null");
    domainNamespace = namespaces.get(2);

    // start DB
    logger.info("Start DB in namespace: {0}, dbListenerPort: {1}, dbUrl: {2}, dbImage: {3}",
        dbNamespace, dbListenerPort, dbUrl, DB_IMAGE_TO_USE_IN_SPEC);
    assertDoesNotThrow(() -> setupDB(DB_IMAGE_TO_USE_IN_SPEC, dbNamespace, getNextFreePort(), dbListenerPort),
        String.format("Failed to setup DB in the namespace %s with dbUrl %s, dbListenerPost %s",
            dbNamespace, dbUrl, dbListenerPort));

    // install operator with DomainOnPvSimplification=true"
    HelmParams opHelmParams =
        new HelmParams().releaseName(OPERATOR_RELEASE_NAME)
            .namespace(opNamespace)
            .chartDir(OPERATOR_CHART_DIR);
    installAndVerifyOperator(opNamespace, opNamespace + "-sa", false,
        0, opHelmParams, ELASTICSEARCH_HOST, false, true, null,
        null, false, "INFO", "DomainOnPvSimplification=true", false, domainNamespace);

    // create pull secrets for domainNamespace when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createBaseRepoSecret(domainNamespace);
  }

  /**
   * User creates RCU, Operate creates PV/PVC and FMW domain
   * Verify Pod is ready and service exists for both admin server and managed servers.
   */
  @Test
  @Order(1)
  @DisplayName("Create a FMW domain on PV when user per-creates RCU")
  void testFmwDomainOnPvUserCreatesRCU() {

    final String pvName = getUniqueName(domainUid1 + "-pv-");
    final String pvcName = getUniqueName(domainUid1 + "-pvc-");

    //create RCU schema
    assertDoesNotThrow(() -> createRcuSchema(FMWINFRA_IMAGE_TO_USE_IN_SPEC, RCUSCHEMAPREFIX + "1",
        dbUrl, dbNamespace),"create RCU schema failed");

    // create a model property file
    File fmwModelPropFile = createWdtPropertyFile(domainUid1, RCUSCHEMAPREFIX + "1");

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        adminSecretName1,
        domainNamespace,
        ADMIN_USERNAME_DEFAULT,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", adminSecretName1));

    // create RCU access secret
    logger.info("Creating RCU access secret: {0}, with prefix: {1}, dbUrl: {2}, schemapassword: {3})",
        rcuaccessSecretName1, RCUSCHEMAPREFIX + "1", RCUSCHEMAPASSWORD, dbUrl);
    assertDoesNotThrow(() -> createRcuAccessSecret(
        rcuaccessSecretName1,
        domainNamespace,
        RCUSCHEMAPREFIX + "1",
        RCUSCHEMAPASSWORD,
        dbUrl),
        String.format("createSecret failed for %s", rcuaccessSecretName1));

    logger.info("Create OPSS wallet password secret");
    assertDoesNotThrow(() -> createOpsswalletpasswordSecret(
        opsswalletpassSecretName1,
        domainNamespace,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", opsswalletpassSecretName1));

    List<String> modelList = new ArrayList<>();
    modelList.add(MODEL_DIR + "/" + fmwModelFile);
    List<String> modelProList = new ArrayList<>();
    modelProList.add(fmwModelPropFile.toPath().toString());
    String miiAuxiliaryImage1Tag = "jrf1" + MII_BASIC_IMAGE_TAG;
    WitParams witParams =
        new WitParams()
            .modelImageName(MII_AUXILIARY_IMAGE_NAME)
            .modelImageTag(miiAuxiliaryImage1Tag)
            .modelFiles(modelList)
            .modelVariableFiles(modelProList);
    createAndPushAuxiliaryImage(MII_AUXILIARY_IMAGE_NAME, miiAuxiliaryImage1Tag, witParams);
    domainCreationImage1 =
        new DomainCreationImage().image(MII_AUXILIARY_IMAGE_NAME + ":" + miiAuxiliaryImage1Tag);

    // create a domain custom resource configuration object
    logger.info("Creating domain custom resource with pvName: {0}", pvName);
    DomainResource domain = createDomainResourceSimplifyJrfPv(
        domainUid1, domainNamespace, adminSecretName1,
        TEST_IMAGES_REPO_SECRET_NAME,
        rcuaccessSecretName1,
        opsswalletpassSecretName1, null,
        pvName, pvcName, Collections.singletonList(domainCreationImage1), null);

    createDomainAndVerify(domain, domainNamespace);

    // verify that all servers are ready
    verifyDomainReady(domainNamespace, domainUid1, replicaCount, "nosuffix");
  }

  /**
   * Export the OPSS wallet file secret of Fmw domain from the previous run
   * Use this OPSS wallet file secret to create Fmw domain on PV to connect to the same database
   * Verify Pod is ready and service exists for both admin server and managed servers.
   */
  @Test
  @Order(2)
  @DisplayName("Create a FMW domain on PV when user provide OPSS wallet file secret")
  void testFmwDomainOnPvUserProvideOpss() {

    final String pvName = getUniqueName(domainUid1 + "-pv-");
    final String pvcName = getUniqueName(domainUid1 + "-pvc-");

    saveAndRestoreOpssWalletfileSecret(domainNamespace, domainUid1, opsswalletfileSecretName1);
    logger.info("Deleting domain custom resource with namespace: {0}, domainUid {1}", domainNamespace, domainUid1);
    deleteDomainResource(domainNamespace, domainUid1);
    try {
      deleteDirectory(Paths.get("/share").toFile());
    } catch (IOException ioe) {
      logger.severe("Failed to cleanup directory /share", ioe);
    }
    logger.info("Creating domain custom resource with pvName: {0}", pvName);
    DomainResource domain = createDomainResourceSimplifyJrfPv(
        domainUid1, domainNamespace, adminSecretName1,
        TEST_IMAGES_REPO_SECRET_NAME,
        rcuaccessSecretName1,
        opsswalletpassSecretName1, opsswalletfileSecretName1,
        pvName, pvcName, Collections.singletonList(domainCreationImage1), null);

    createDomainAndVerify(domain, domainNamespace);

    // verify that all servers are ready and EM console is accessible
    verifyDomainReady(domainNamespace, domainUid1, replicaCount, "nosuffix");

    // delete the domain
    deleteDomainResource(domainNamespace, domainUid1);
    //delete the rcu pod
    assertDoesNotThrow(() -> deletePod("rcu", dbNamespace),
              "Got exception while deleting server " + "rcu");
    checkPodDoesNotExist("rcu", null, dbNamespace);
    //delete the wallet file ewallet.p12
    try {
      delete(new File("./ewallet.p12"));
      logger.info("Wallet file ewallet.p12 is deleted");
    } catch (IOException ioe) {
      logger.severe("Failed to delete file ewallet.p12", ioe);
    }

  }

  /**
   * The user provides opss.walletFileSecret that does not exist.
   * In this case the domain will not be created and operator will log message like
   * "Domain xxx is not valid: OpssWalletFile secret 'xxx' not found in namespace xxx"
   */
  @Test
  @Order(3)
  @DisplayName("Create a FMW domain on PV when user provide OPSS wallet file secret that does not exist")
  void testFmwDomainOnPvUserProvideOpssNotexist() {
    String domainUid = "jrfdomainonpv-userrcu2";
    String adminSecretName = domainUid + "-weblogic-credentials";
    String rcuaccessSecretName = domainUid + "-rcu-credentials";
    String opsswalletpassSecretName = domainUid + "-opss-wallet-password-secret";
    String opsswalletfileSecretName = domainUid + "-opss-wallet-file-secret";
    final String pvName = getUniqueName(domainUid1 + "-pv-");
    final String pvcName = getUniqueName(domainUid1 + "-pvc-");

    //create RCU schema
    assertDoesNotThrow(() -> createRcuSchema(FMWINFRA_IMAGE_TO_USE_IN_SPEC, RCUSCHEMAPREFIX + "2",
        dbUrl, dbNamespace),"create RCU schema failed");

    // create a model property file
    File fmwModelPropFile = createWdtPropertyFile(domainUid, RCUSCHEMAPREFIX + "2");

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        adminSecretName,
        domainNamespace,
        ADMIN_USERNAME_DEFAULT,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", adminSecretName));

    // create RCU access secret
    logger.info("Creating RCU access secret: {0}, with prefix: {1}, dbUrl: {2}, schemapassword: {3})",
        rcuaccessSecretName, RCUSCHEMAPREFIX + "2", RCUSCHEMAPASSWORD, dbUrl);
    assertDoesNotThrow(() -> createRcuAccessSecret(
        rcuaccessSecretName,
        domainNamespace,
        RCUSCHEMAPREFIX + "2",
        RCUSCHEMAPASSWORD,
        dbUrl),
        String.format("createSecret failed for %s", rcuaccessSecretName));

    logger.info("Create OPSS wallet password secret");
    assertDoesNotThrow(() -> createOpsswalletpasswordSecret(
        opsswalletpassSecretName,
        domainNamespace,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", opsswalletpassSecretName));

    List<String> modelList = new ArrayList<>();
    modelList.add(MODEL_DIR + "/" + fmwModelFile);
    List<String> modelProList = new ArrayList<>();
    modelProList.add(fmwModelPropFile.toPath().toString());
    String miiAuxiliaryImageTag = "jrf2" + MII_BASIC_IMAGE_TAG;
    WitParams witParams =
        new WitParams()
            .modelImageName(MII_AUXILIARY_IMAGE_NAME)
            .modelImageTag(miiAuxiliaryImageTag)
            .modelFiles(modelList)
            .modelVariableFiles(modelProList);
    createAndPushAuxiliaryImage(MII_AUXILIARY_IMAGE_NAME, miiAuxiliaryImageTag, witParams);
    DomainCreationImage domainCreationImage =
        new DomainCreationImage().image(MII_AUXILIARY_IMAGE_NAME + ":" + miiAuxiliaryImageTag);

    // create a domain custom resource configuration object
    logger.info("Creating domain custom resource with pvName: {0}", pvName);
    DomainResource domain = createDomainResourceSimplifyJrfPv(
        domainUid, domainNamespace, adminSecretName,
        TEST_IMAGES_REPO_SECRET_NAME,
        rcuaccessSecretName,
        opsswalletpassSecretName, opsswalletfileSecretName,
        pvName, pvcName, Collections.singletonList(domainCreationImage), null);

    createDomainAndVerify(domain, domainNamespace);

    String expectedErrorMsg = String.format("Domain %s is not valid: OpssWalletFile secret '%s'"
        + " not found in namespace '%s'", domainUid, opsswalletfileSecretName, domainNamespace);
    String operatorPodName =
        assertDoesNotThrow(() -> getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace));
    verifyPodWithExpectedErrorMsg(expectedErrorMsg, operatorPodName, opNamespace);
    
    // delete the domain
    deleteDomainResource(domainNamespace, domainUid);
    //delete the rcu pod
    assertDoesNotThrow(() -> deletePod("rcu", dbNamespace),
              "Got exception while deleting server " + "rcu");
    checkPodDoesNotExist("rcu", null, dbNamespace);

  }

  /**
   * User creates RCU, Operate creates PV/PVC and FMW domain with multiple images
   * Verify Pod is ready and service exists for both admin server and managed servers.
   */
  @Test
  @Order(4)
  @DisplayName("Create a FMW domain on PV with multiple images when user per-creates RCU")
  void testFmwDomainOnPvUserCreatesRCUMultiImages() {
    String domainUid = "jrfdomainonpv-userrcu3";
    String adminSecretName = domainUid + "-weblogic-credentials";
    String rcuaccessSecretName = domainUid + "-rcu-credentials";
    String opsswalletpassSecretName = domainUid + "-opss-wallet-password-secret";
    final String pvName = getUniqueName(domainUid + "-pv-");
    final String pvcName = getUniqueName(domainUid + "-pvc-");

    //create RCU schema
    assertDoesNotThrow(() -> createRcuSchema(FMWINFRA_IMAGE_TO_USE_IN_SPEC, RCUSCHEMAPREFIX + "3",
        dbUrl, dbNamespace), "create RCU schema failed");

    // create a model property file
    File fmwModelPropFile = createWdtPropertyFile(domainUid, RCUSCHEMAPREFIX + "3");

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        adminSecretName,
        domainNamespace,
        ADMIN_USERNAME_DEFAULT,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", adminSecretName));

    // create RCU access secret
    logger.info("Creating RCU access secret: {0}, with prefix: {1}, dbUrl: {2}, schemapassword: {3})",
        rcuaccessSecretName, RCUSCHEMAPREFIX + "3", RCUSCHEMAPASSWORD, dbUrl);
    assertDoesNotThrow(() -> createRcuAccessSecret(
        rcuaccessSecretName,
        domainNamespace,
        RCUSCHEMAPREFIX + "3",
        RCUSCHEMAPASSWORD,
        dbUrl),
        String.format("createSecret failed for %s", rcuaccessSecretName));

    logger.info("Create OPSS wallet password secret");
    assertDoesNotThrow(() -> createOpsswalletpasswordSecret(
        opsswalletpassSecretName,
        domainNamespace,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", opsswalletpassSecretName));
    
    DomainCreationImage domainCreationImage1 = createImage(fmwModelFile,fmwModelPropFile,"jrf3");

    // image2 with model files for jms config
    List modelList = new ArrayList<>();
    modelList.add(MODEL_DIR + "/model.jms2.yaml");
    String miiAuxiliaryImageTag = "jrf3jms" + MII_BASIC_IMAGE_TAG;
    WitParams witParams =
        new WitParams()
            .modelImageName(MII_AUXILIARY_IMAGE_NAME)
            .modelImageTag(miiAuxiliaryImageTag)
            .wdtModelOnly(true)
            .modelFiles(modelList)
            .wdtVersion("NONE");
    createAndPushAuxiliaryImage(MII_AUXILIARY_IMAGE_NAME, miiAuxiliaryImageTag, witParams);
    DomainCreationImage domainCreationImage2 = new DomainCreationImage().image(MII_AUXILIARY_IMAGE_NAME
        + ":" + miiAuxiliaryImageTag);
    List<DomainCreationImage> domainCreationImages = new ArrayList<>();
    domainCreationImages.add(domainCreationImage1);
    domainCreationImages.add(domainCreationImage2);

    // create a domain custom resource configuration object
    logger.info("Creating domain custom resource with pvName: {0}", pvName);
    DomainResource domain = createDomainResourceSimplifyJrfPv(
        domainUid, domainNamespace, adminSecretName,
        TEST_IMAGES_REPO_SECRET_NAME,
        rcuaccessSecretName,
        opsswalletpassSecretName, null,
        pvName, pvcName, domainCreationImages, null);

    createDomainAndVerify(domain, domainNamespace);

    // verify that all servers are ready
    verifyDomainReady(domainNamespace, domainUid, replicaCount, "nosuffix");

    //create router for admin service on OKD
    String adminServerPodName = domainUid + "-admin-server";
    String adminSvcExtHost = createRouteForOKD(getExternalServicePodName(adminServerPodName), domainNamespace);
    logger.info("admin svc host = {0}", adminSvcExtHost);

    // check configuration for JMS
    checkConfiguredJMSresouce(domainNamespace, adminServerPodName, adminSvcExtHost);

    // delete the domain
    deleteDomainResource(domainNamespace, domainUid);
    //delete the rcu pod
    assertDoesNotThrow(() -> deletePod("rcu", dbNamespace),
              "Got exception while deleting server " + "rcu");
    checkPodDoesNotExist("rcu", null, dbNamespace);

  }

  /**
   * User creates RCU, Operate creates PV/PVC and FMW domain with additional WDT config map.
   * Verify Pod is ready and service exists for both admin server and managed servers.
   */
  @Test
  @Order(5)
  @DisplayName("Create a FMW domain on PV with additional WDT config map when user per-creates RCU")
  void testFmwDomainOnPvUserCreatesRCUwdtConfigMap() {

    final String pvName = getUniqueName(domainUid4 + "-pv-");
    final String pvcName = getUniqueName(domainUid4 + "-pvc-");

    //create RCU schema
    assertDoesNotThrow(() -> createRcuSchema(FMWINFRA_IMAGE_TO_USE_IN_SPEC, RCUSCHEMAPREFIX + "4",
        dbUrl, dbNamespace), "create RCU schema failed");

    // create a model property file
    File fmwModelPropFile = createWdtPropertyFile(domainUid4, RCUSCHEMAPREFIX + "4");

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        adminSecretName4,
        domainNamespace,
        ADMIN_USERNAME_DEFAULT,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", adminSecretName4));

    // create RCU access secret
    logger.info("Creating RCU access secret: {0}, with prefix: {1}, dbUrl: {2}, schemapassword: {3})",
        rcuaccessSecretName4, RCUSCHEMAPREFIX + "4", RCUSCHEMAPASSWORD, dbUrl);
    assertDoesNotThrow(() -> createRcuAccessSecret(
        rcuaccessSecretName4,
        domainNamespace,
        RCUSCHEMAPREFIX + "4",
        RCUSCHEMAPASSWORD,
        dbUrl),
        String.format("createSecret failed for %s", rcuaccessSecretName4));

    logger.info("Create OPSS wallet password secret");
    assertDoesNotThrow(() -> createOpsswalletpasswordSecret(
        opsswalletpassSecretName4,
        domainNamespace,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", opsswalletpassSecretName4));

    DomainCreationImage domainCreationImage = createImage(fmwModelFile,fmwModelPropFile,"jrf4");
    domainCreationImages4.add(domainCreationImage);

    logger.info("create WDT configMap with jms model");
    configMapName = "jmsconfigmap";
    createConfigMapAndVerify(
        configMapName, domainUid4, domainNamespace,
        Arrays.asList(MODEL_DIR + "/model.jms2.yaml"));

    // create a domain custom resource configuration object
    logger.info("Creating domain custom resource with pvName: {0}", pvName);
    DomainResource domain = createDomainResourceSimplifyJrfPv(
        domainUid4, domainNamespace, adminSecretName4,
        TEST_IMAGES_REPO_SECRET_NAME,
        rcuaccessSecretName4,
        opsswalletpassSecretName4, null,
        pvName, pvcName, domainCreationImages4, configMapName);

    createDomainAndVerify(domain, domainNamespace);

    // verify that all servers are ready
    verifyDomainReady(domainNamespace, domainUid4, replicaCount, "nosuffix");

    //create router for admin service on OKD
    String adminServerPodName = domainUid4 + "-admin-server";
    String adminSvcExtHost = createRouteForOKD(getExternalServicePodName(adminServerPodName), domainNamespace);
    logger.info("admin svc host = {0}", adminSvcExtHost);

    // check configuration for JMS
    checkConfiguredJMSresouce(domainNamespace, adminServerPodName, adminSvcExtHost);

  }

  /**
   * The user provides opss.walletFileSecret that is empty.
   * If "ewallet.p12" is an empty file, running opss-wallet.sh to restore the wallet file
   * secret will fail and return "Error: Wallet file 'ewallet.p12' is empty"
   * Create opss.walletFileSecret without entry with --from-file=walletFile to get an empty walletFileSecret
   * The operator will not mount the secret but proceed with normal domain creation without error
   */
  @Test
  @Order(6)
  @DisplayName("Create a FMW domain on PV when user provide OPSS wallet file is empty")
  void testFmwDomainOnPvUserProvideEmptyOpss() {

    final String pvName = getUniqueName(domainUid4 + "-pv-");
    final String pvcName = getUniqueName(domainUid4 + "-pvc-");

    //create empty wallet file ewallet.p12
    try {
      File file = new File("ewallet.p12");
      if (file.createNewFile()) {
        logger.info("Empty wallet file ewallet.p12 is created: " + file.getAbsolutePath());
      } else {
        logger.info("Failed to create file ewallet.p12 ");
      }
    } catch (IOException ioe) {
      logger.severe("Failed to create file ewallet.p12", ioe);
    }

    ExecResult result = restoreOpssWalletfileSecret(domainNamespace, domainUid4, opsswalletfileSecretName4);
    logger.info("restoreOpssWalletfileSecret returns msg: " + result.stdout());
    assertTrue(result.stdout().contains("Error: Wallet file 'ewallet.p12' is empty"));

    //delete the empty wallet file ewallet.p12
    try {
      delete(new File("./ewallet.p12"));
      logger.info("Wallet file ewallet.p12 is deleted");
    } catch (IOException ioe) {
      logger.severe("Failed to delete file ewallet.p12", ioe);
    }

    //create empty walletFileSecret
    createOpsswalletFileSecretWithoutFile(opsswalletfileSecretName4, domainNamespace);
    logger.info("Empty walletFile secret {0} is created in the namespace {1}",
        opsswalletfileSecretName4, domainNamespace);

    logger.info("Deleting domain custom resource with namespace: {0}, domainUid {1}", domainNamespace, domainUid4);
    deleteDomainResource(domainNamespace, domainUid4);
    try {
      deleteDirectory(Paths.get("/share").toFile());
    } catch (IOException ioe) {
      logger.severe("Failed to cleanup directory /share", ioe);
    }
    logger.info("Creating domain custom resource with pvName: {0}", pvName);
    DomainResource domain = createDomainResourceSimplifyJrfPv(
        domainUid4, domainNamespace, adminSecretName4,
        TEST_IMAGES_REPO_SECRET_NAME,
        rcuaccessSecretName4,
        opsswalletpassSecretName4, opsswalletfileSecretName4,
        pvName, pvcName, domainCreationImages4, configMapName);

    createDomainAndVerify(domain, domainNamespace);

    // verify that all servers are ready
    verifyDomainReady(domainNamespace, domainUid4, replicaCount, "nosuffix");

    // delete the domain
    deleteDomainResource(domainNamespace, domainUid4);
    //delete the rcu pod
    assertDoesNotThrow(() -> deletePod("rcu", dbNamespace),
              "Got exception while deleting server " + "rcu");
    checkPodDoesNotExist("rcu", null, dbNamespace);

  }

  private DomainCreationImage createImage(String fmwModelFile,  File fmwModelPropFile, String imageTagPrefix) {

    List<String> modelList = new ArrayList<>();
    modelList.add(MODEL_DIR + "/" + fmwModelFile);
    List<String> modelProList = new ArrayList<>();
    modelProList.add(fmwModelPropFile.toPath().toString());
    String miiAuxiliaryImageTag = imageTagPrefix + MII_BASIC_IMAGE_TAG;
    WitParams witParams =
        new WitParams()
            .modelImageName(MII_AUXILIARY_IMAGE_NAME)
            .modelImageTag(miiAuxiliaryImageTag)
            .modelFiles(modelList)
            .modelVariableFiles(modelProList);
    createAndPushAuxiliaryImage(MII_AUXILIARY_IMAGE_NAME, miiAuxiliaryImageTag, witParams);
    return new DomainCreationImage().image(MII_AUXILIARY_IMAGE_NAME + ":" + miiAuxiliaryImageTag);

  }

  private File createWdtPropertyFile(String domainUid, String rcuSchemaPrefix) {

    Properties p = new Properties();
    p.setProperty("rcuDb", dbUrl);
    p.setProperty("rcuSchemaPrefix", rcuSchemaPrefix);
    p.setProperty("rcuSchemaPassword", RCUSCHEMAPASSWORD);
    p.setProperty("adminUsername", ADMIN_USERNAME_DEFAULT);
    p.setProperty("adminPassword", ADMIN_PASSWORD_DEFAULT);
    p.setProperty("domainName", domainUid);

    // create a model property file
    File domainPropertiesFile = assertDoesNotThrow(() ->
        File.createTempFile(fmwModelFilePrefix, ".properties"),
        "Failed to create FMW model properties file");

    // create the property file
    assertDoesNotThrow(() ->
        p.store(new FileOutputStream(domainPropertiesFile), "FMW properties file"),
        "Failed to write FMW properties file");

    return domainPropertiesFile;
  }

  /**
   * Start Oracle DB instance in the specified namespace.
   *
   * @param dbImage image name of database
   * @param dbNamespace namespace where DB and RCU schema are going to start
   * @param dbPort NodePort of DB
   * @param dbListenerPort TCP listener port of DB
   * @throws ApiException if any error occurs when setting up database
   */
  private static synchronized void setupDB(String dbImage, String dbNamespace, int dbPort, int dbListenerPort)
      throws ApiException {
    LoggingFacade logger = getLogger();
    // create pull secrets when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createBaseRepoSecret(dbNamespace);

    if (OKD) {
      addSccToDBSvcAccount("default", dbNamespace);
    }

    logger.info("Start Oracle DB with dbImage: {0}, dbPort: {1}, dbNamespace: {2}, dbListenerPort:{3}",
        dbImage, dbPort, dbNamespace, dbListenerPort);
    startOracleDB(dbImage, dbPort, dbNamespace, dbListenerPort);
  }

  /**
   * Check Configured JMS Resource.
   *
   * @param domainNamespace domain namespace
   * @param adminServerPodName  admin server pod name
   * @param adminSvcExtHost admin server external host
   */
  private static void checkConfiguredJMSresouce(String domainNamespace, String adminServerPodName,
                                               String adminSvcExtHost) {
    verifyConfiguredSystemResource(domainNamespace, adminServerPodName, adminSvcExtHost,
        "JMSSystemResources", "TestClusterJmsModule2", "200");
  }

  private void verifyPodWithExpectedErrorMsg(String expectedErrorMsg, String podName, String nameSpace) {

    logger.info("Verifying operator pod log for error messages");
    testUntil(
        () -> assertDoesNotThrow(() -> checkPodLogContains(expectedErrorMsg, podName, nameSpace),
            String.format("Checking operator pod %s log failed for namespace %s, expectErrorMsg %s", podName,
               nameSpace, expectedErrorMsg)),
        logger,
        "Checking operator log containing the expected error msg {0}:",
        expectedErrorMsg);
  }
  
}