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

package com.facebook.buck.rules;

import com.facebook.buck.event.AbstractBuckEvent;
import com.facebook.buck.event.EventKey;
import com.facebook.buck.event.LeafEvent;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.CaseFormat;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

/**
 * Base class for events about build rules.
 */
public abstract class ArtifactCacheEvent extends AbstractBuckEvent implements LeafEvent {
  public enum Operation {
    FETCH,
    STORE,
    COMPRESS,
    DECOMPRESS,
  }

  private final Operation operation;
  @JsonIgnore
  private final ImmutableSet<RuleKey> ruleKeys;

  protected ArtifactCacheEvent(
      EventKey eventKey,
      Operation operation,
      ImmutableSet<RuleKey> ruleKeys) {
    super(eventKey);
    this.operation = operation;
    this.ruleKeys = ruleKeys;
  }

  @Override
  public String getCategory() {
    return "artifact_" + operation.toString().toLowerCase();
  }

  @Override
  public String getValueString() {
    return String.format(
        "%s:%s",
        operation.toString().toLowerCase(),
        Joiner.on(",").join(ruleKeys));
  }

  public ImmutableSet<RuleKey> getRuleKeys() {
    return ruleKeys;
  }

  public Operation getOperation() {
    return operation;
  }

  public static Started started(Operation operation, ImmutableSet<RuleKey> ruleKeys) {
    return new Started(operation, ruleKeys);
  }

  public static Finished finished(Started started) {
    return new Finished(started, Optional.<CacheResult>absent());
  }

  public static Finished finished(Started started, CacheResult cacheResult) {
    return new Finished(started, Optional.of(cacheResult));
  }

  public static class Started extends ArtifactCacheEvent {
    protected Started(Operation operation, ImmutableSet<RuleKey> ruleKeys) {
      super(EventKey.unique(), operation, ruleKeys);
    }

    @Override
    public String getEventName() {
      return String.format("Artifact%sCacheStarted",
          CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, getOperation().toString()));
    }
  }

  public static class Finished extends ArtifactCacheEvent {
    /** Not present iff {@link #getOperation()} is not {@link Operation#FETCH}. */
    private final Optional<CacheResult> cacheResult;

    public Optional<CacheResult> getCacheResult() {
      return cacheResult;
    }

    protected Finished(
        Started started,
        Optional<CacheResult> cacheResult) {
      super(started.getEventKey(), started.getOperation(), started.getRuleKeys());
      Preconditions.checkArgument(
          (started.getOperation().equals(Operation.FETCH) && cacheResult.isPresent()) ||
          (!started.getOperation().equals(Operation.FETCH) && !cacheResult.isPresent()),
          "For FETCH operations, cacheResult must be non-null. " +
          "For non-FETCH operations, cacheResult must be null.");
      this.cacheResult = cacheResult;
    }

    public boolean isSuccess() {
      return !cacheResult.isPresent() || cacheResult.get().getType().isSuccess();
    }

    @Override
    public String getEventName() {
      return String.format("Artifact%sCacheFinished",
          CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, getOperation().toString()));
    }

    @Override
    public boolean equals(Object o) {
      if (!super.equals(o)) {
        return false;
      }
      // Because super.equals compares the EventKey, getting here means that we've somehow managed
      // to create 2 Finished events for the same Started event.
      throw new UnsupportedOperationException("Multiple conflicting Finished events detected.");
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(super.hashCode(), cacheResult);
    }
  }

}
