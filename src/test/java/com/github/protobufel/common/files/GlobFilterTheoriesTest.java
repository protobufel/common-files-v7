//
// Copyright © 2014, David Tesler (https://github.com/protobufel)
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//     * Redistributions of source code must retain the above copyright
//       notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above copyright
//       notice, this list of conditions and the following disclaimer in the
//       documentation and/or other materials provided with the distribution.
//     * Neither the name of the <organization> nor the
//       names of its contributors may be used to endorse or promote products
//       derived from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
// ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
// WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
// DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
// DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
// LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
// ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
//

package com.github.protobufel.common.files;

//import static com.github.protobufel.common.verifications.Verifications.assertNonNull;
//import static com.github.protobufel.common.verifications.Verifications.verifyNonNull;

import com.github.protobufel.common.files.resources.Resources.FileSet;
import com.github.protobufel.common.files.resources.Resources.IFileSet;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assume.assumeThat;

//import static org.hamcrest.Matchers.*;

@RunWith(Theories.class)
public class GlobFilterTheoriesTest {
  @DataPoints public static final Path[] dirs = {Paths.get("test1"), Paths.get("test2/test2/")};
  @SuppressWarnings("null")
  public static final List<String> subDirs =
      Arrays.asList("dir1", "dir2/dir21/dir211", "dir2/dir22/dir221", "dir2/dir22/dir222/dir2221");
  @SuppressWarnings("null")
  public static final List<String> subFiles =
      Arrays.asList(
          "file1",
          "file2.txt",
          "file3.ok",
          // "file4." will become file4 on Windows!
          "file4");
  @SuppressWarnings("null")
  private static final Logger log = LoggerFactory.getLogger(GlobFilterTheoriesTest.class);
  @SuppressWarnings("null")
  private static final FileSystem DEFAULT_FS = FileSystems.getDefault();
  //TODO make my own rule based on nio Files!
  @Rule public TemporaryFolder temp = new TemporaryFolder();
  private Path tempPath = Objects.requireNonNull(Paths.get(""));

  @DataPoints({"globs"})
  public static MatchData[] globs() {
    final ImmutableList.Builder<MatchData> builder =
        ImmutableList.<MatchData>builder()
            .add(
                MatchData.of(
                    "glob:*",
                    false,
                    false,
                    false,
                    true,
                    (hasItems("dir1", "dir2")))) //TODO: and items are nonNull
            .add(
                MatchData.of(
                    "glob:*", false, false, true, false, hasItems(subFiles.toArray(new String[0]))))
            .add(
                MatchData.of(
                    "glob:dir2",
                    false,
                    false,
                    true,
                    true,
                    (Matcher<? extends Iterable<? extends String>>) everyItem(equalTo("dir2"))))
            .add(
                MatchData.of(
                    "glob:dir2/**/dir22?/dir*/*.*",
                    false,
                    false,
                    true,
                    false,
                    (Matcher<? extends Iterable<? extends String>>)
                        everyItem(
                            allOf(
                                startsWith(toFileSystemSpecific("dir2/dir22/dir222/dir2221")),
                                anyOf(
                                    endsWith("file2.txt"), endsWith("file3.ok"), endsWith("file4")),
                                not(anyOf(endsWith("file4"), endsWith("file1")))))));
    return Objects.requireNonNull(builder.build().toArray(new MatchData[0]));
  }

  @SuppressWarnings("null")
  private static String toFileSystemSpecific(final String canonicalPath) {
    return Utils.isUnix()
        ? canonicalPath
        : canonicalPath.replaceAll("/", (File.separator + File.separator));
  }

  @Before
  public void setUp() throws Exception {
    tempPath = Objects.requireNonNull(temp.getRoot().toPath());
    assumeThat(tempPath.getFileSystem(), equalTo(DEFAULT_FS));
  }

  @After
  public void tearDown() throws Exception {}

  @Theory(nullsAccepted = false)
  public void globShouldMatchPartially(final MatchData matchData, final Path relativeDir)
      throws IOException {
    assumeThat(relativeDir, is(notNullValue()));
    assumeThat(relativeDir.getFileSystem(), equalTo(tempPath.getFileSystem()));
    assumeThat(relativeDir.isAbsolute(), is(false));

    final @NonNull Path dir = Objects.requireNonNull(tempPath.resolve(relativeDir).normalize());
    assumeThat(dir.isAbsolute(), is(true));

    makeTestTree(dir);

    final IFileSet fileSet =
        FileSet.builder(matchData.getFileSet())
            .directory(Objects.requireNonNull(dir.toString()))
            .build();
    final Iterable<Path> actualPaths =
        PathVisitors.getResourceFiles(
            tempPath, fileSet, matchData.isFollowLinks(), matchData.isAllowDuplicates(), log);
    assertThat(
        actualPaths,
        everyItem(both(notNullValue(Path.class)).and(hasToString(startsWith(dir.toString())))));

    final List<String> actualRelativePaths =
        FluentIterable.from(actualPaths)
            .transform(
                new Function<Path, String>() {
                  @Override
                  public String apply(@Nullable Path input) {
                    return Objects.requireNonNull(
                        dir.relativize(Objects.requireNonNull(input)).toString());
                  }
                })
            .toList();

    assertThat(actualRelativePaths, matchData.getExpectations());
  }

  private void makeTestTree(final Path dir) throws IOException {
    makeTestTree(dir, subDirs, subFiles);
  }

  private void makeTestTree(
      final Path dir, final Iterable<String> subDirs, final Iterable<String> files)
      throws IOException {
    final Path realDir = Files.createDirectories(Objects.requireNonNull(dir));

    for (String subDir : Objects.requireNonNull(subDirs)) {
      Files.createDirectories(realDir.resolve(Objects.requireNonNull(subDir)));
    }

    Files.walkFileTree(
        dir,
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            super.postVisitDirectory(dir, exc);

            for (String file : files) {
              final Path filePath = dir.resolve(file);

              if (Files.notExists(filePath)) {
                Files.createFile(filePath);
              }
            }

            return FileVisitResult.CONTINUE;
          }
        });
  }

  public static final class MatchData {
    final IFileSet fileSet;
    final boolean followLinks;
    final boolean allowDuplicates;
    final Matcher<Iterable<String>> expectations;

    public MatchData(
        final IFileSet fileSet,
        boolean followLinks,
        boolean allowDuplicates,
        Matcher<? extends Iterable<? extends String>> expectations) {
      this.fileSet = Objects.requireNonNull(fileSet);
      this.followLinks = followLinks;
      this.allowDuplicates = allowDuplicates;
      @SuppressWarnings("unchecked")
      Matcher<Iterable<String>> castExpectations = (Matcher<Iterable<String>>) expectations;
      this.expectations = castExpectations;
    }

    public static MatchData of(
        final String syntaxAndPattern,
        final boolean followLinks,
        final boolean allowDuplicates,
        final boolean allowFiles,
        final boolean allowDirs,
        final Matcher<? extends Iterable<? extends String>> expectations) {
      final IFileSet fileSet =
          FileSet.builder()
              .addIncludes(syntaxAndPattern)
              .allowDirs(allowDirs)
              .allowFiles(allowFiles)
              .build();
      return new MatchData(fileSet, followLinks, allowDuplicates, expectations);
    }

    public IFileSet getFileSet() {
      return fileSet;
    }

    public boolean isFollowLinks() {
      return followLinks;
    }

    public boolean isAllowDuplicates() {
      return allowDuplicates;
    }

    public Matcher<Iterable<String>> getExpectations() {
      return expectations;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      builder
          .append("MatchData [fileSet=")
          .append(fileSet)
          .append(", followLinks=")
          .append(followLinks)
          .append(", allowDuplicates=")
          .append(allowDuplicates)
          .append(", expectations=")
          .append(expectations)
          .append("]");
      return Objects.requireNonNull(builder.toString());
    }
  }
}
