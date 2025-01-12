/*
 * Copyright 2014-present Facebook, Inc.
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

package com.facebook.buck.apple;

import com.facebook.buck.cli.BuckConfig;
import com.facebook.buck.log.Logger;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.util.ProcessExecutor;
import com.facebook.buck.util.ProcessExecutorParams;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AppleConfig {
  private static final Pattern CODE_SIGN_IDENTITY_PATTERN =
      Pattern.compile("([A-F0-9]{40}) \"(iPhone.*)\"");
  private static final Logger LOG = Logger.get(AppleConfig.class);

  private final BuckConfig delegate;

  public AppleConfig(BuckConfig delegate) {
    this.delegate = delegate;
  }

  /**
   * If specified, the value of {@code [apple] xcode_developer_dir} wrapped in a {@link Supplier}.
   * Otherwise, this returns a {@link Supplier} that lazily runs {@code xcode-select --print-path}
   * and caches the result.
   */
  public Supplier<Optional<Path>> getAppleDeveloperDirectorySupplier(
      ProcessExecutor processExecutor) {
    Optional<String> xcodeDeveloperDirectory = delegate.getValue("apple", "xcode_developer_dir");
    if (xcodeDeveloperDirectory.isPresent()) {
      Path developerDirectory = delegate.resolvePathThatMayBeOutsideTheProjectFilesystem(
          Paths.get(xcodeDeveloperDirectory.get()));
      return Suppliers.ofInstance(Optional.of(developerDirectory));
    } else {
      return createAppleDeveloperDirectorySupplier(processExecutor);
    }
  }

  public ImmutableList<Path> getExtraToolchainPaths() {
    ImmutableList<String> extraPathsStrings = delegate.getListWithoutComments(
        "apple",
        "extra_toolchain_paths");
    return ImmutableList.copyOf(Lists.transform(
        extraPathsStrings,
        new Function<String, Path>() {
            @Override
            public Path apply(String string) {
                return Paths.get(string);
            }
        }));
  }

  public ImmutableList<Path> getExtraPlatformPaths() {
    ImmutableList<String> extraPathsStrings = delegate.getListWithoutComments(
        "apple",
        "extra_platform_paths");
    return ImmutableList.copyOf(Lists.transform(
        extraPathsStrings,
        new Function<String, Path>() {
            @Override
            public Path apply(String string) {
                return Paths.get(string);
            }
        }));
  }

  public ImmutableMap<AppleSdk, AppleSdkPaths> getAppleSdkPaths(ProcessExecutor processExecutor) {
    Optional<Path> appleDeveloperDirectory =
        getAppleDeveloperDirectorySupplier(processExecutor).get();
    try {
      ImmutableMap<String, AppleToolchain> toolchains =
          AppleToolchainDiscovery.discoverAppleToolchains(
              appleDeveloperDirectory,
              getExtraToolchainPaths());
      return AppleSdkDiscovery.discoverAppleSdkPaths(
          appleDeveloperDirectory,
          getExtraPlatformPaths(),
          toolchains);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * @return a memoizing {@link Supplier} that caches the output of
   *     {@code security -v -p codesigning} to find all valid code signing identities.
   */
  public static Supplier<ImmutableSet<CodeSignIdentity>> createCodeSignIdentitiesSupplier(
      final ProcessExecutor processExecutor) {
    return Suppliers.memoize(new Supplier<ImmutableSet<CodeSignIdentity>>() {
      @Override
      public ImmutableSet<CodeSignIdentity> get() {
        ProcessExecutorParams processExecutorParams =
            ProcessExecutorParams.builder()
                .setCommand(
                    ImmutableList.of(
                        "security", "find-identity",
                        "-v", "-p", "codesigning"))
                .build();
        // Must specify that stdout is expected or else output may be wrapped in Ansi escape chars.
        Set<ProcessExecutor.Option> options = EnumSet.of(ProcessExecutor.Option.EXPECTING_STD_OUT);
        ProcessExecutor.Result result;
        try {
          result = processExecutor.launchAndExecute(
              processExecutorParams,
              options,
              /* stdin */ Optional.<String>absent(),
              /* timeOutMs */ Optional.<Long>absent(),
              /* timeOutHandler */ Optional.<Function<Process, Void>>absent());
        } catch (InterruptedException | IOException e) {
          LOG.warn("Could not execute security, continuing without codesign identity.");
          return ImmutableSet.of();
        }

        if (result.getExitCode() != 0) {
          throw new RuntimeException("security -v -p codesigning failed: " + result.getStderr());
        }

        Matcher matcher = CODE_SIGN_IDENTITY_PATTERN.matcher(result.getStdout().get());
        ImmutableSet.Builder<CodeSignIdentity> builder = ImmutableSet.builder();
        while (matcher.find()) {
          String hash = matcher.group(1);
          String fullName = matcher.group(2);
          CodeSignIdentity identity = CodeSignIdentity.builder()
              .setHash(hash).setFullName(fullName).build();
          builder.add(identity);
          LOG.debug("Found code signing identity: " + identity.toString());
        }
        ImmutableSet<CodeSignIdentity> allValidIdentities = builder.build();
        if (allValidIdentities.isEmpty()) {
          LOG.warn("No valid code signing identities found.  Device build/install won't work.");
        } else if (allValidIdentities.size() > 1) {
          LOG.warn(
              "More than 1 valid identity found.  This could potentially  cause the wrong one to" +
                  " be used unless explicitly specified via CODE_SIGN_IDENTITY.");
        }
        return allValidIdentities;
      }
    });
  }

  /**
   * @return a memoizing {@link Supplier} that caches the output of
   *     {@code xcode-select --print-path}.
   */
  private static Supplier<Optional<Path>> createAppleDeveloperDirectorySupplier(
      final ProcessExecutor processExecutor) {
    return Suppliers.memoize(new Supplier<Optional<Path>>() {
      @Override
      public Optional<Path> get() {
        ProcessExecutorParams processExecutorParams =
            ProcessExecutorParams.builder()
                .setCommand(ImmutableList.of("xcode-select", "--print-path"))
                .build();
        // Must specify that stdout is expected or else output may be wrapped in Ansi escape chars.
        Set<ProcessExecutor.Option> options = EnumSet.of(ProcessExecutor.Option.EXPECTING_STD_OUT);
        ProcessExecutor.Result result;
        try {
          result = processExecutor.launchAndExecute(
              processExecutorParams,
              options,
              /* stdin */ Optional.<String>absent(),
              /* timeOutMs */ Optional.<Long>absent(),
              /* timeOutHandler */ Optional.<Function<Process, Void>>absent());
        } catch (InterruptedException | IOException e) {
          LOG.warn("Could not execute xcode-select, continuing without developer dir.");
          return Optional.absent();
        }

        if (result.getExitCode() != 0) {
          throw new RuntimeException("xcode-select --print-path failed: " + result.getStderr());
        }

        return Optional.of(Paths.get(result.getStdout().get().trim()));
      }
    });
  }

  public Optional<String> getTargetSdkVersion(ApplePlatform platform) {
    return delegate.getValue("apple", platform.getName() + "_target_sdk_version");
  }

  public ImmutableList<String> getXctestPlatformNames() {
    return delegate.getListWithoutComments(
        "apple",
        "xctest_platforms");
  }

  public Optional<Path> getXctoolPath() {
    return getOptionalPath("apple", "xctool_path");
  }

  public Optional<BuildTarget> getXctoolZipTarget() {
    return delegate.getBuildTarget("apple", "xctool_zip_target");
  }

  public Optional<String> getXctoolDefaultDestinationSpecifier() {
    return delegate.getValue("apple", "xctool_default_destination_specifier");
  }

  public boolean getXcodeDisableParallelizeBuild() {
    return delegate.getBooleanValue("apple", "xcode_disable_parallelize_build", false);
  }

  public Optional<Path> getAppleDeviceHelperPath() {
    return getOptionalPath("apple", "device_helper_path");
  }

  private Optional<Path> getOptionalPath(String sectionName, String propertyName) {
    Optional<String> pathString = delegate.getValue(sectionName, propertyName);
    if (pathString.isPresent()) {
      return Optional.of(Paths.get(pathString.get()));
    } else {
      return Optional.absent();
    }
  }
}
