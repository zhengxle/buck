/*
 * Copyright 2019-present Facebook, Inc.
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
package com.facebook.buck.support.build.report;

import com.facebook.buck.core.model.BuildId;
import com.facebook.buck.core.util.log.Logger;
import com.facebook.buck.doctor.DefectReport;
import com.facebook.buck.doctor.DefectReporter;
import com.facebook.buck.doctor.DefectSubmitResult;
import com.facebook.buck.util.environment.BuildEnvironmentDescription;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import okhttp3.FormBody;
import okhttp3.RequestBody;

/**
 * Uploads the ruleKeyLoggerFile to trigger a cache analysis, and then uploads a rage report id to
 * link it to it's corresponding build.
 */
public class RuleKeyLogFileUploader {
  private static final Logger LOG = Logger.get(RuleKeyLogFileUploader.class);

  private DefectReporter defectReporter;
  private BuildEnvironmentDescription buildEnvironmentDescription;
  private RequestUploader requestUploader;

  public RuleKeyLogFileUploader(
      DefectReporter defectReporter,
      BuildEnvironmentDescription buildEnvironmentDescription,
      URL url,
      long timeout,
      BuildId buildId) {
    requestUploader = new RequestUploader(url, timeout, buildId);
    this.defectReporter = defectReporter;
    this.buildEnvironmentDescription = buildEnvironmentDescription;
  }

  /**
   * By uploading a rage report with the ruleKeyLoggerFile, we trigger a cache analysis and
   * subsequent store of report object. To associate that with the build, we upload the rage report
   * id generated by the rage endpoint to the build report endpoint. The frontend server accepts and
   * incomplete report so we send it with the minimum information for it to generate and store the
   * cache analysis.
   *
   * @param ruleKeyLoggerFilepath the filepath where the corresponding ruleKeyLoggerFile is in.
   */
  public void uploadRuleKeyLogFile(Path ruleKeyLoggerFilepath) {

    // This defect report is mostly incomplete, it's not used as a rage report itself, only as a
    // way to generate the cache analysis.
    DefectReport report =
        DefectReport.builder()
            .setBuildEnvironmentDescription(buildEnvironmentDescription)
            .setIncludedPaths(ImmutableSet.of(ruleKeyLoggerFilepath))
            .setHighlightedBuildIds(
                ImmutableList.of(BuildId.fromJson(requestUploader.getBuildId())))
            .build();
    try {
      DefectSubmitResult result = defectReporter.submitReport(report);

      if (!result.getReportId().isPresent()) {
        throw new IllegalStateException("The id of the rage report must be present");
      }

      String rageReportId = result.getReportId().get();

      RequestBody formBody = new FormBody.Builder().add("rage_report_id", rageReportId).build();

      requestUploader.uploadRequest(formBody);

    } catch (IOException e) {
      LOG.warn(e, "Error while submitting minimal rage report with rule key logger file");
    }
  }
}