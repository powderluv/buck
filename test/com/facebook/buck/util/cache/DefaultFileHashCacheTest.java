/*
 * Copyright 2013-present Facebook, Inc.
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

package com.facebook.buck.util.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.facebook.buck.testutil.integration.DebuggableTemporaryFolder;
import com.facebook.buck.util.HashCodeAndFileType;
import com.google.common.hash.HashCode;

import org.hamcrest.junit.ExpectedException;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DefaultFileHashCacheTest {

  @Rule
  public DebuggableTemporaryFolder tmp = new DebuggableTemporaryFolder();

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Test
  public void whenPathIsPutCacheContainsPath() {
    DefaultFileHashCache cache =
        new DefaultFileHashCache(new FakeProjectFilesystem());
    Path path = new File("SomeClass.java").toPath();
    HashCodeAndFileType value = HashCodeAndFileType.of(
        HashCode.fromInt(42),
        HashCodeAndFileType.Type.FILE);
    cache.loadingCache.put(path, value);
    assertTrue("Cache should contain path", cache.contains(path));
  }

  @Test
  public void whenPathIsPutPathGetReturnsHash() throws IOException {
    DefaultFileHashCache cache =
        new DefaultFileHashCache(new FakeProjectFilesystem());
    Path path = new File("SomeClass.java").toPath();
    HashCodeAndFileType value = HashCodeAndFileType.of(
        HashCode.fromInt(42),
        HashCodeAndFileType.Type.FILE);
    cache.loadingCache.put(path, value);
    assertEquals("Cache should contain hash", value.getHashCode(), cache.get(path));
  }

  @Test
  public void whenPathIsPutThenInvalidatedCacheDoesNotContainPath() {
    DefaultFileHashCache cache =
        new DefaultFileHashCache(new FakeProjectFilesystem());
    Path path = new File("SomeClass.java").toPath();
    HashCodeAndFileType value = HashCodeAndFileType.of(
        HashCode.fromInt(42),
        HashCodeAndFileType.Type.FILE);
    cache.loadingCache.put(path, value);
    assertTrue("Cache should contain path", cache.contains(path));
    cache.invalidate(path);
    assertFalse("Cache should not contain pain", cache.contains(path));
  }

  @Test
  public void invalidatingNonExistentEntryDoesNotThrow() {
    DefaultFileHashCache cache =
        new DefaultFileHashCache(new FakeProjectFilesystem());
    Path path = new File("SomeClass.java").toPath();
    assertFalse("Cache should not contain pain", cache.contains(path));
    cache.invalidate(path);
    assertFalse("Cache should not contain pain", cache.contains(path));
  }

  @Test
  public void missingEntryThrowsNoSuchFileException() throws IOException {
    DefaultFileHashCache cache = new DefaultFileHashCache(new FakeProjectFilesystem());
    expectedException.expect(NoSuchFileException.class);
    cache.get(Paths.get("hello.java"));
  }

  @Test
  public void whenPathsArePutThenInvalidateAllRemovesThem() throws IOException {
    ProjectFilesystem filesystem = new FakeProjectFilesystem();
    DefaultFileHashCache cache = new DefaultFileHashCache(filesystem);

    Path path1 = Paths.get("path1");
    filesystem.writeContentsToPath("contenst1", path1);
    cache.get(path1);
    assertTrue(cache.contains(path1));

    Path path2 = Paths.get("path2");
    filesystem.writeContentsToPath("contenst2", path2);
    cache.get(path2);
    assertTrue(cache.contains(path2));

    // Verify that `invalidateAll` clears everything from the cache.
    cache.invalidateAll();
    assertFalse(cache.contains(path1));
    assertFalse(cache.contains(path2));
  }

}
