/*
 * Copyright 2012-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.java;

import static org.junit.Assert.assertEquals;

import com.facebook.buck.java.runner.FileClassPathRunner;
import com.facebook.buck.model.BuildId;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.TestExecutionContext;
import com.facebook.buck.test.selectors.TestSelectorList;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.facebook.buck.testutil.MoreAsserts;
import com.facebook.buck.testutil.TestConsole;
import com.facebook.buck.util.Verbosity;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.easymock.EasyMock;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

public class JUnitStepTest {

  @Test
  public void testGetShellCommand() throws IOException {
    Set<Path> classpathEntries = ImmutableSet.of(
        Paths.get("foo"),
        Paths.get("bar/baz"));

    String testClass1 = "com.facebook.buck.shell.JUnitCommandTest";
    String testClass2 = "com.facebook.buck.shell.InstrumentCommandTest";
    Set<String> testClassNames = ImmutableSet.of(testClass1, testClass2);

    String vmArg1 = "-Dname1=value1";
    String vmArg2 = "-Dname1=value2";
    List<String> vmArgs = ImmutableList.of(vmArg1, vmArg2);

    BuildId pretendBuildId = new BuildId("pretend-build-id");
    String buildIdArg = String.format("-D%s=%s", JUnitStep.BUILD_ID_PROPERTY, pretendBuildId);

    Path modulePath = Paths.get("module/submodule");
    String modulePathArg = String.format(
        "-D%s=%s",
        JUnitStep.MODULE_BASE_PATH_PROPERTY,
        modulePath);

    Path directoryForTestResults = Paths.get("buck-out/gen/theresults/");
    Path directoryForTemp = Paths.get("buck-out/gen/thetmp/");
    boolean isCodeCoverageEnabled = false;
    boolean isDebugEnabled = false;
    Path testRunnerClasspath = Paths.get("build/classes/junit");
    FakeProjectFilesystem filesystem = new FakeProjectFilesystem();

    JUnitStep junit = new JUnitStep(
        filesystem,
        classpathEntries,
        testClassNames,
        vmArgs,
        /* nativeLibsEnvironment */ ImmutableMap.<String, String>of(),
        directoryForTestResults,
        modulePath,
        directoryForTemp,
        isCodeCoverageEnabled,
        isDebugEnabled,
        pretendBuildId,
        TestSelectorList.empty(),
        /* isDryRun */ false,
        TestType.JUNIT,
        testRunnerClasspath,
        /* testRuleTimeoutMs*/ Optional.<Long>absent(),
        /* stdOutLogLevel */ Optional.<Level>absent(),
        /* stdErrLogLevel */ Optional.<Level>absent(),
        /* pathToJavAgent */ Optional.<String>absent());

    ExecutionContext executionContext = EasyMock.createMock(ExecutionContext.class);
    EasyMock.expect(executionContext.getVerbosity()).andReturn(Verbosity.ALL);
    EasyMock.expect(executionContext.getDefaultTestTimeoutMillis()).andReturn(5000L);
    EasyMock.replay(executionContext);

    List<String> observedArgs = junit.getShellCommand(executionContext);
    MoreAsserts.assertListEquals(
        ImmutableList.of(
            "java",
            "-Djava.io.tmpdir=" + filesystem.resolve(directoryForTemp),
            "-Dbuck.testrunner_classes=" + testRunnerClasspath,
            buildIdArg,
            modulePathArg,
            vmArg1,
            vmArg2,
            "-verbose",
            "-classpath",
            Joiner.on(File.pathSeparator).join(
                "@" + filesystem.resolve(junit.getClassPathFile()),
                Paths.get("build/classes/junit")),
            FileClassPathRunner.class.getName(),
            JUnitStep.JUNIT_TEST_RUNNER_CLASS_NAME,
            directoryForTestResults.toString(),
            "5000",
            "",
            "",
            testClass1,
            testClass2),
        observedArgs);

    EasyMock.verify(executionContext);
  }

  @Test
  public void ensureThatDebugFlagCausesJavaDebugCommandFlagToBeAdded() {
    Set<Path> classpathEntries = ImmutableSet.of(
        Paths.get("foo"),
        Paths.get("bar/baz"));

    String testClass1 = "com.facebook.buck.shell.JUnitCommandTest";
    String testClass2 = "com.facebook.buck.shell.InstrumentCommandTest";
    Set<String> testClassNames = ImmutableSet.of(testClass1, testClass2);

    String vmArg1 = "-Dname1=value1";
    String vmArg2 = "-Dname1=value2";
    List<String> vmArgs = ImmutableList.of(vmArg1, vmArg2);

    BuildId pretendBuildId = new BuildId("pretend-build-id");
    String buildIdArg = String.format("-D%s=%s", JUnitStep.BUILD_ID_PROPERTY, pretendBuildId);

    Path modulePath = Paths.get("module/submodule");
    String modulePathArg = String.format(
        "-D%s=%s",
        JUnitStep.MODULE_BASE_PATH_PROPERTY,
        modulePath);

    Path directoryForTestResults = Paths.get("buck-out/gen/theresults/");
    Path directoryForTemp = Paths.get("buck-out/gen/thetmp/");
    boolean isCodeCoverageEnabled = false;
    boolean isDebugEnabled = true;
    Path testRunnerClasspath = Paths.get("build/classes/junit");

    JUnitStep junit = new JUnitStep(
        FakeProjectFilesystem.createJavaOnlyFilesystem(),
        classpathEntries,
        testClassNames,
        vmArgs,
        ImmutableMap.<String, String>of(),
        directoryForTestResults,
        modulePath,
        directoryForTemp,
        isCodeCoverageEnabled,
        isDebugEnabled,
        pretendBuildId,
        TestSelectorList.empty(),
        /* isDryRun */ false,
        TestType.JUNIT,
        testRunnerClasspath,
        /* testRuleTimeoutMs*/ Optional.<Long>absent(),
        /* stdOutLogLevel */ Optional.<Level>absent(),
        /* stdErrLogLevel */ Optional.<Level>absent(),
        /* pathToJavaAgent */ Optional.<String> absent());

    TestConsole console = new TestConsole(Verbosity.ALL);
    ExecutionContext executionContext = TestExecutionContext.newBuilder()
        .setConsole(console)
        .setDebugEnabled(true)
        .build();

    List<String> observedArgs = junit.getShellCommand(executionContext);
    MoreAsserts.assertListEquals(
        ImmutableList.of(
            "java",
            "-Djava.io.tmpdir=/opt/src/buck/" + directoryForTemp,
            "-Dbuck.testrunner_classes=" + testRunnerClasspath,
            buildIdArg,
            modulePathArg,
            "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005",
            vmArg1,
            vmArg2,
            "-verbose",
            "-classpath",
            Joiner.on(File.pathSeparator).join(
                "@/opt/src/buck/" + junit.getClassPathFile(),
                Paths.get("build/classes/junit")),
            FileClassPathRunner.class.getName(),
            JUnitStep.JUNIT_TEST_RUNNER_CLASS_NAME,
            directoryForTestResults.toString(),
            "0",
            "",
            "",
            testClass1,
            testClass2),
        observedArgs);

    // TODO(simons): Why does the CapturingPrintStream append spaces?
    assertEquals("Debugging. Suspending JVM. Connect a JDWP debugger to port 5005 to proceed.",
        console.getTextWrittenToStdErr().trim());
  }
}
