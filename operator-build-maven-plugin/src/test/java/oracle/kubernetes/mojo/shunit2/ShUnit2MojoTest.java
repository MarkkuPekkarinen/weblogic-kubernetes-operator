// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.mojo.shunit2;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import com.meterware.simplestub.StaticStubSupport;
import oracle.kubernetes.mojosupport.MojoTestBase;
import oracle.kubernetes.mojosupport.TestFileSystem;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.junit.Before;
import org.junit.Test;
import org.objectweb.asm.ClassReader;

import static com.meterware.simplestub.Stub.createNiceStub;
import static org.apache.maven.plugins.annotations.LifecyclePhase.TEST;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContainingInAnyOrder;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.fail;

@SuppressWarnings("UnconstructableJUnitTestCase") // mistaken warning due to private constructor
public class ShUnit2MojoTest extends MojoTestBase {

  private static final String TEST_SCRIPT = "shunit2";

  private final ShUnit2Mojo mojo;
  private final Function<String, BashProcessBuilder> builderFunction = this::createProcessBuilder;
  private final TestDelegate delegate = new TestDelegate();
  private final TestFileSystem fileSystem = new TestFileSystem();
  private static final File TEST_CLASSES_DIRECTORY = new File("/test-classes");
  private static final File LATEST_SHUNIT2_DIRECTORY = new File(TEST_CLASSES_DIRECTORY, "shunit2/2.1.8");
  private static final File EARLIER_SHUNIT2_DIRECTORY = new File(TEST_CLASSES_DIRECTORY, "shunit2/2.1.6");
  private static final File SOURCE_DIRECTORY = new File("/sources");
  private static final File TEST_SOURCE_DIRECTORY = new File("/tests");

  public ShUnit2MojoTest() {
    this(new ShUnit2Mojo());
  }

  private ShUnit2MojoTest(ShUnit2Mojo mojo) {
    super(mojo);
    this.mojo = mojo;
  }

  @Before
  public void setUp() throws Exception {
    ClassReader classReader = new ClassReader(ShUnit2Mojo.class.getName());
    classReader.accept(new Visitor(ShUnit2Mojo.class), 0);

    mementos.add(StaticStubSupport.install(ShUnit2Mojo.class, "fileSystem", fileSystem));
    mementos.add(StaticStubSupport.install(TestSuite.class, "builderFunction", builderFunction));

    setMojoParameter("outputDirectory", TEST_CLASSES_DIRECTORY);
    setMojoParameter("sourceDirectory", SOURCE_DIRECTORY);
    setMojoParameter("testSourceDirectory", TEST_SOURCE_DIRECTORY);
    silenceMojoLog();

    fileSystem.defineFileContents(new File(LATEST_SHUNIT2_DIRECTORY, TEST_SCRIPT), "");
    fileSystem.defineFileContents(new File(TEST_SOURCE_DIRECTORY, "test1.sh"), "");
  }

  BashProcessBuilder createProcessBuilder(String command) {
    return new BashProcessBuilder(delegate, command);
  }

  @Test
  public void mojoAnnotatedWithName() {
    assertThat(getClassAnnotation(Mojo.class).getField("name"), equalTo("shunit2"));
  }

  @Test
  public void mojoAnnotatedWithDefaultPhase() {
    assertThat(getClassAnnotation(Mojo.class).getField("defaultPhase"), equalTo(TEST));
  }

  @Test
  public void hasOutputDirectoryField_withAnnotation() throws NoSuchFieldException {
    Field classPathField = ShUnit2Mojo.class.getDeclaredField("outputDirectory");
    assertThat(classPathField.getType(), equalTo(File.class));
    assertThat(
        getFieldAnnotation(classPathField, Parameter.class).getField("defaultValue"),
        equalTo("${project.build.testOutputDirectory}"));
  }

  @Test
  public void hasTestSourceDirectoryField_withAnnotation() throws NoSuchFieldException {
    Field classPathField = ShUnit2Mojo.class.getDeclaredField("testSourceDirectory");
    assertThat(classPathField.getType(), equalTo(File.class));
    assertThat(
        getFieldAnnotation(classPathField, Parameter.class).getField("defaultValue"),
        equalTo("${project.basedir}/src/test/sh"));
  }

  @Test
  public void hasSourceDirectoryField_withAnnotation() throws NoSuchFieldException {
    Field classPathField = ShUnit2Mojo.class.getDeclaredField("sourceDirectory");
    assertThat(classPathField.getType(), equalTo(File.class));
    assertThat(
        getFieldAnnotation(classPathField, Parameter.class).getField("defaultValue"),
        equalTo("${project.basedir}/src/main/sh"));
  }

  @Test
  public void useCopiedShUnit2Directory() throws MojoExecutionException {
    fileSystem.defineFileContents(new File(LATEST_SHUNIT2_DIRECTORY, TEST_SCRIPT), "");

    assertThat(mojo.getEffectiveShUnit2Directory(), equalTo(LATEST_SHUNIT2_DIRECTORY));
  }

  @Test
  public void whenMultipleShUnit2VersionsInstalled_selectLatest() throws MojoExecutionException {
    fileSystem.defineFileContents(new File(EARLIER_SHUNIT2_DIRECTORY, TEST_SCRIPT), "");
    fileSystem.defineFileContents(new File(LATEST_SHUNIT2_DIRECTORY, TEST_SCRIPT), "");

    assertThat(mojo.getEffectiveShUnit2Directory(), equalTo(LATEST_SHUNIT2_DIRECTORY));
  }

  @Test
  public void whenLatestShUnit2VersionsMissing_selectPrior() throws MojoExecutionException {
    fileSystem.clear();
    fileSystem.defineFileContents(new File(EARLIER_SHUNIT2_DIRECTORY, TEST_SCRIPT), "");
    fileSystem.defineFileContents(LATEST_SHUNIT2_DIRECTORY, "");

    assertThat(mojo.getEffectiveShUnit2Directory(), equalTo(EARLIER_SHUNIT2_DIRECTORY));
  }

  @Test(expected = MojoExecutionException.class)
  public void whenShUnit2NotInstalled_reportFailure() throws MojoFailureException, MojoExecutionException {
    fileSystem.clear();
    fileSystem.defineFileContents(TEST_CLASSES_DIRECTORY, "");

    executeMojo();
  }

  @Test
  public void onExecution_specifyTheSelectedShUnit2ScriptPath() throws MojoFailureException, MojoExecutionException {
    executeMojo();

    assertThat(delegate.getShUnit2ScriptPath(), equalTo(LATEST_SHUNIT2_DIRECTORY + "/shunit2"));
  }

  @Test
  public void onExecution_specifyPathToSourceScripts() throws MojoFailureException, MojoExecutionException {
    executeMojo();

    assertThat(delegate.getSourceScriptDir(), equalTo(SOURCE_DIRECTORY.getAbsolutePath()));
  }

  @Test
  public void onExecution_specifyTestScripts() throws MojoFailureException, MojoExecutionException {
    fileSystem.defineFileContents(new File(TEST_SOURCE_DIRECTORY, "test1.sh"), "");
    fileSystem.defineFileContents(new File(TEST_SOURCE_DIRECTORY, "test3.sh"), "");
    fileSystem.defineFileContents(new File(TEST_SOURCE_DIRECTORY, "nothing.sh"), "");
    fileSystem.defineFileContents(new File(TEST_SOURCE_DIRECTORY, "2ndtest.sh"), "");
    fileSystem.defineFileContents(new File(TEST_SOURCE_DIRECTORY, "4thtest"), "");

    executeMojo();

    assertThat(delegate.getScriptPaths(),
          arrayContainingInAnyOrder("/tests/test1.sh", "/tests/2ndtest.sh", "/tests/test3.sh", "/tests/4thtest"));
  }

  @Test
  public void onExecution_logOutputs() throws MojoFailureException, MojoExecutionException {
    defineExecution().withOutputs("This is an example", "and here is another", "Ran 2 tests.");

    executeMojo();

    assertThat(getInfoLines(), contains("This is an example", "and here is another",
                    createExpectedSuccessSummary(2, "test1.sh")));
  }

  protected ProcessStub defineExecution() {
    return delegate.defineScriptExecution();
  }

  private String createExpectedSuccessSummary(int numTestsRun, String testScript) {
    return AnsiUtils.text("Tests run: " + numTestsRun).asGreen().format()
          + String.format(", Failures: 0, Errors: 0 - in /tests/%s", testScript);
  }

  @Test
  public void whenErrorDetected_reportInSummary() throws MojoExecutionException {
    defineExecution().withErrors("This is an example", "and here is another").withOutputs("Ran 3 tests.");

    try {
      executeMojo();
      fail("Should have thrown an exception");
    } catch (MojoFailureException ignored) {
      assertThat(getInfoLines(),
            contains(createExpectedFailureSummary(3, 0, 2, "test1.sh")));
    }
  }

  private String createExpectedFailureSummary(int numTestsRun, int numFailures, int numErrors, String testScript) {
    return AnsiUtils.text("Tests run: " + numTestsRun).asBold().asRed().format()
          + String.format(", Failures: %d, Errors: %d - in /tests/%s", numFailures, numErrors, testScript);
  }

  @Test
  public void onExecution_logErrors() throws MojoFailureException, MojoExecutionException {
    defineExecution().withErrors("This is an example", "and here is another");

    try {
      executeMojo();
      fail("Should have thrown an exception");
    } catch (MojoFailureException ignored) {
      assertThat(getErrorLines(), contains("This is an example", "and here is another"));
    }
  }

  @Test
  public void onExecution_ignoreNonZeroReturnCodeErrors() throws MojoFailureException, MojoExecutionException {
    defineExecution().withErrors("This is an example",
          "\u001B[1;31mERROR:\u001B[0m testPartyLikeItIs1999() returned non-zero return code.");

    try {
      executeMojo();
      fail("Should have thrown an exception");
    } catch (MojoFailureException ignored) {
      assertThat(getErrorLines(), contains("This is an example"));
    }
  }

  @Test
  public void onExecution_ignoreFailureMessage() throws MojoFailureException, MojoExecutionException {
    defineExecution().withOutputs("This is an example", "FAILED (failures=2)");

    executeMojo();

    assertThat(getErrorLines(), empty());
  }

  @Test
  public void onExecution_recordReportedNumberOfTests() throws MojoFailureException, MojoExecutionException {
    fileSystem.defineFileContents(new File(TEST_SOURCE_DIRECTORY, "test1.sh"), "");
    fileSystem.defineFileContents(new File(TEST_SOURCE_DIRECTORY, "test2.sh"), "");
    defineExecution().withOutputs(String.format("Ran %s tests.", AnsiUtils.text("3").asBold().asBlue().format()));
    defineExecution().withOutputs(String.format("Ran %s tests.", AnsiUtils.text("2").asBold().asBlue().format()));

    executeMojo();

    assertThat(mojo.getTestSuites().stream().map(TestSuite::numTestsRun).collect(Collectors.toList()), contains(3, 2));
  }

  @Test
  public void onExecution_recordReportedNumberOfFailures() throws MojoFailureException, MojoExecutionException {
    fileSystem.defineFileContents(new File(TEST_SOURCE_DIRECTORY, "test1.sh"), "");
    fileSystem.defineFileContents(new File(TEST_SOURCE_DIRECTORY, "test2.sh"), "");
    defineExecution()
          .withOutputs("test1", "test2", createExpectedTestFailure("expected up but was down"))
          .withErrors("test2 returned non-zero return code.");
    defineExecution()
          .withOutputs("test3", createExpectedTestFailure("expected blue but was red"),
                       "test4", createExpectedTestFailure("expected left but was right"));

    try {
      executeMojo();
    } catch (MojoFailureException e) {
      assertThat(getFailuresByTestSuite(), contains(1, 2));
    }
  }

  @Nonnull
  protected List<Integer> getFailuresByTestSuite() {
    return mojo.getTestSuites().stream().map(TestSuite::numFailures).collect(Collectors.toList());
  }

  private String createExpectedTestFailure(String explanation) {
    return AnsiUtils.text("ASSERT:").asBold().asRed().format() + explanation;
  }

  @Test(expected = MojoFailureException.class)
  public void whenAnyTestsFail_mojoThrowsException() throws MojoFailureException, MojoExecutionException {
    fileSystem.defineFileContents(new File(TEST_SOURCE_DIRECTORY, "test1.sh"), "");
    fileSystem.defineFileContents(new File(TEST_SOURCE_DIRECTORY, "test2.sh"), "");
    defineExecution()
          .withOutputs("test1", "test2", createExpectedTestFailure("expected up but was down"))
          .withErrors("test2 returned non-zero return code.");
    defineExecution()
          .withOutputs("test3", createExpectedTestFailure("expected blue but was red"),
                       "test4", createExpectedTestFailure("expected left but was right"));

    executeMojo();
  }

  // todo print tests run, failures at end of each testsuite
  // todo print total tests run, total failures across multiple tests

  static class TestDelegate implements BiFunction<String, Map<String, String>, Process> {
    private final ArrayDeque<ProcessStub> processStubs = new ArrayDeque<>();
    private final List<String> commands = new ArrayList<>();
    private Map<String, String> environmentVariables;

    ProcessStub defineScriptExecution() {
      final ProcessStub processStub = createNiceStub(ProcessStub.class);
      processStubs.add(processStub);
      return processStub;
    }

    String getShUnit2ScriptPath() {
      return environmentVariables.get(ShUnit2Mojo.SHUNIT2_PATH);
    }

    String getSourceScriptDir() {
      return environmentVariables.get(ShUnit2Mojo.SCRIPTPATH);
    }

    String[] getScriptPaths() {
      return commands.toArray(new String[0]);
    }

    @Override
    public Process apply(String command, Map<String, String> environmentVariables) {
      this.commands.add(command);
      this.environmentVariables = environmentVariables;
      return Optional.ofNullable(processStubs.pollFirst()).orElseGet(this::defineScriptExecution);
    }
  }


  abstract static class ProcessStub extends Process {
    private final List<String> outputLines = new ArrayList<>();
    private final List<String> errorLines = new ArrayList<>();

    ProcessStub withOutputs(String... lines) {
      outputLines.addAll(Arrays.asList(lines));
      return this;
    }

    ProcessStub withErrors(String... lines) {
      errorLines.addAll(Arrays.asList(lines));
      return this;
    }

    @Override
    public InputStream getInputStream() {
      return new StringListInputStream(outputLines);
    }

    @Override
    public InputStream getErrorStream() {
      return new StringListInputStream(errorLines);
    }
  }

  static class StringListInputStream extends ByteArrayInputStream {

    public StringListInputStream(List<String> inputs) {
      super(toByteArray(inputs));
    }

    private static byte[] toByteArray(List<String> inputs) {
      return String.join(System.lineSeparator(), inputs).getBytes(StandardCharsets.UTF_8);
    }
  }
}
