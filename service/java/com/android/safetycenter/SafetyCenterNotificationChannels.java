/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.safetycenter;

import static android.os.Build.VERSION_CODES.TIRAMISU;

import android.annotation.Nullable;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Binder;
import android.os.UserHandle;
import android.safetycenter.SafetySourceData;
import android.safetycenter.SafetySourceIssue;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.android.permission.util.UserUtils;
import com.android.safetycenter.resources.SafetyCenterResourcesContext;

import java.util.List;

/** Class responsible for creating and updating Safety Center's notification channels. */
@RequiresApi(TIRAMISU)
final class SafetyCenterNotificationChannels {

    private static final String TAG = "SafetyCenterNC";

    private static final String CHANNEL_GROUP_ID = "safety_center_channels";
    private static final String CHANNEL_ID_INFORMATION = "safety_center_information";
    private static final String CHANNEL_ID_RECOMMENDATION = "safety_center_recommendation";
    private static final String CHANNEL_ID_CRITICAL_WARNING = "safety_center_critical_warning";

    private final SafetyCenterResourcesContext mResourcesContext;

    SafetyCenterNotificationChannels(SafetyCenterResourcesContext safetyCenterResourceContext) {
        mResourcesContext = safetyCenterResourceContext;
    }

    /** Returns a {@link NotificationManager} which will send notifications to the given user. */
    @Nullable
    static NotificationManager getNotificationManagerForUser(
            Context baseContext, UserHandle userHandle) {
        Context contextAsUser = baseContext.createContextAsUser(userHandle, 0);
        return contextAsUser.getSystemService(NotificationManager.class);
    }

    /**
     * Returns the ID of the appropriate {@link NotificationChannel} for a notification about the
     * given {@code issue} after ensuring that channel has been created.
     */
    @Nullable
    String getCreatedChannelId(NotificationManager notificationManager, SafetySourceIssue issue) {
        try {
            createAllChannelsWithoutCallingIdentity(notificationManager);
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to create notification channels", e);
            return null;
        }
        return getChannelIdForIssue(issue);
    }

    /**
     * Creates all Safety Center {@link NotificationChannel}s instances and their group, for all
     * current users, dropping any calling identity so those channels can be unblockable. Throws a
     * {@link RuntimeException} if any channel is malformed and could not be created.
     */
    void createAllChannelsForAllUsers(Context context) {
        List<UserHandle> users = UserUtils.getUserHandles(context);
        for (int i = 0; i < users.size(); i++) {
            createAllChannelsForUser(context, users.get(i));
        }
    }

    /**
     * Creates all Safety Center {@link NotificationChannel}s instances and their group for the
     * given {@link UserHandle}, dropping any calling identity so those channels can be unblockable.
     * Throws a {@link RuntimeException} if any channel is malformed and could not be created.
     */
    void createAllChannelsForUser(Context context, UserHandle user) {
        try {
            createAllChannelsWithoutCallingIdentity(getNotificationManagerForUser(context, user));
        } catch (RuntimeException e) {
            Log.w(TAG, "Error creating notification channels for user " + user.getIdentifier(), e);
        }
    }

    @Nullable
    private String getChannelIdForIssue(SafetySourceIssue issue) {
        switch (issue.getSeverityLevel()) {
            case SafetySourceData.SEVERITY_LEVEL_INFORMATION:
                return CHANNEL_ID_INFORMATION;
            case SafetySourceData.SEVERITY_LEVEL_RECOMMENDATION:
                return CHANNEL_ID_RECOMMENDATION;
            case SafetySourceData.SEVERITY_LEVEL_CRITICAL_WARNING:
                return CHANNEL_ID_CRITICAL_WARNING;
            default:
                Log.w(TAG, "No applicable notification channel for issue " + issue);
                return null;
        }
    }

    /**
     * Creates all Safety Center {@link NotificationChannel}s instances and their group using the
     * given {@link NotificationManager}, dropping any calling identity so those channels can be
     * unblockable. Throws a {@link RuntimeException} if any channel is malformed and could not be
     * created.
     */
    // TODO(b/265277413): Recreate/update these channels on locale changes by calling this method
    @Nullable
    private void createAllChannelsWithoutCallingIdentity(NotificationManager notificationManager) {
        // Clearing calling identity to be able to make unblockable system notification channels
        final long callingId = Binder.clearCallingIdentity();
        try {
            notificationManager.createNotificationChannelGroup(getChannelGroupDefinition());
            notificationManager.createNotificationChannel(getInformationChannelDefinition());
            notificationManager.createNotificationChannel(getRecommendationChannelDefinition());
            notificationManager.createNotificationChannel(getCriticalWarningChannelDefinition());
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    private NotificationChannelGroup getChannelGroupDefinition() {
        return new NotificationChannelGroup(
                CHANNEL_GROUP_ID, getString("notification_channel_group_name"));
    }

    private NotificationChannel getInformationChannelDefinition() {
        NotificationChannel channel =
                new NotificationChannel(
                        CHANNEL_ID_INFORMATION,
                        getString("notification_channel_name_information"),
                        NotificationManager.IMPORTANCE_LOW);
        channel.setGroup(CHANNEL_GROUP_ID);
        channel.setBlockable(true);
        return channel;
    }

    private NotificationChannel getRecommendationChannelDefinition() {
        NotificationChannel channel =
                new NotificationChannel(
                        CHANNEL_ID_RECOMMENDATION,
                        getString("notification_channel_name_recommendation"),
                        NotificationManager.IMPORTANCE_DEFAULT);
        channel.setGroup(CHANNEL_GROUP_ID);
        channel.setBlockable(false);
        return channel;
    }

    private NotificationChannel getCriticalWarningChannelDefinition() {
        NotificationChannel channel =
                new NotificationChannel(
                        CHANNEL_ID_CRITICAL_WARNING,
                        getString("notification_channel_name_critical_warning"),
                        NotificationManager.IMPORTANCE_HIGH);
        channel.setGroup(CHANNEL_GROUP_ID);
        channel.setBlockable(false);
        return channel;
    }

    private String getString(String name) {
        return mResourcesContext.getStringByName(name);
    }
}
