// Copyright 2018 The Outline Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.outline.log;

import android.content.Context;
import android.util.Log;
import com.getsentry.raven.android.Raven;
import com.getsentry.raven.event.BreadcrumbBuilder;
import com.getsentry.raven.event.Breadcrumb;
import com.getsentry.raven.event.Breadcrumbs;
import com.getsentry.raven.event.EventBuilder;
import java.lang.IllegalStateException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

/**
 * Wrapper class for Sentry (Raven) error reporting framework.
 */
class SentryErrorReporter {
  /**
   * Encapsulates a Sentry message.
   */
  private static class SentryMessage {
    private String msg;
    private Breadcrumb.Level level;
    SentryMessage(final String msg, Breadcrumb.Level level) {
      this.msg = msg;
      this.level = level;
    }
    public Breadcrumb toBreadcrumb() {
      return new BreadcrumbBuilder()
          .setMessage(msg)
          .setLevel(level)
          .build();
    }
  }

  private static boolean isInitialized = false;

  // Queue of messages waiting to be sent once Sentry is initialized.
  private static Queue<SentryMessage> breadcrumbsQueue = new LinkedList<SentryMessage>();

  // Disallow instantiation in favor of a purely static class.
  private SentryErrorReporter() {}

  /**
   * Initializes the error reporting framework with the given credentials.
   * Configures an Android uncaught exception handler which sends events to Sentry.
   *
   * @param context Android application Context
   * @param dsn Sentry provided identifier
   * @throws IllegalStateException if has already been initialized.
   */
  public static void init(Context context, final String dsn) throws IllegalStateException {
    if (isInitialized) {
      throw new IllegalStateException("Error reporting framework already initiated");
    }
    Raven.init(context, dsn, new DataSensitiveRavenFactory(context));
    isInitialized = true;

    // Record all queued breadcrumbs.
    while (breadcrumbsQueue.size() > 0) {
      Breadcrumbs.record(breadcrumbsQueue.remove().toBreadcrumb());
    }
  }

  /**
   * Sends previously recorded errors and messages to Sentry. Associate the report
   * with the provided event id.
   *
   * @param eventId, unique identifier i.e. the event id for a error report in raven-js.
   * @throws IllegalStateException if has not been initialized.
   */
  public static void send(final String eventId) throws IllegalStateException {
    if (!isInitialized) {
      throw new IllegalStateException("Error reporting framework not initiated");
    }
    final String uuid = eventId != null ? eventId : UUID.randomUUID().toString();
    // Associate this report with the event ID generated by Raven JS for cross-referencing. If the
    // ID is not present, use a random UUID to disambiguate the report message so it doesn't get
    // clustered with other reports. Clustering retains the report data on the server side, whereas
    // inactivity results in its deletion after 90 days.
    // Don't build the event so the event builder runs and adds platform data.
    Raven.capture(new EventBuilder()
                      .withMessage(String.format("Android report (%s)", uuid))
                      .withTag("user_event_id", uuid));
  }

  /**
   * Records an exception to be sent with the next error report.
   *
   * @param throwable, exception to record.
   */
  public static void recordException(Throwable throwable) throws IllegalStateException {
    recordErrorMessage(Log.getStackTraceString(throwable));
  }

  /**
   * Records a message with ERROR log level.
   *
   * @param msg, string to log
   */
  public static void recordErrorMessage(final String msg) throws IllegalStateException {
    recordMessage(msg, Breadcrumb.Level.ERROR);
  }

  /**
   * Records a message with WARNING log level.
   *
   * @param msg, string to log
   */
  public static void recordWarningMessage(final String msg) throws IllegalStateException {
    recordMessage(msg, Breadcrumb.Level.WARNING);
  }

  /**
   * Records a message with INFO log level.
   *
   * @param msg, string to log
   */
  public static void recordInfoMessage(final String msg) throws IllegalStateException {
    recordMessage(msg, Breadcrumb.Level.INFO);
  }

  // Record a log message to be sent with the next error report.
  private static void recordMessage(final String msg, Breadcrumb.Level level) {
    if (!isInitialized) {
      breadcrumbsQueue.add(new SentryMessage(msg, level));
      return;
    }
    Breadcrumbs.record(
      new BreadcrumbBuilder()
          .setMessage(msg)
          .setLevel(level)
          .build());
  }
}