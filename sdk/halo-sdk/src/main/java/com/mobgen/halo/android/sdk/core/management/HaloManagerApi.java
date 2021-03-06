package com.mobgen.halo.android.sdk.core.management;

import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.CheckResult;
import android.support.annotation.Keep;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.mobgen.halo.android.framework.api.HaloStorageApi;
import com.mobgen.halo.android.framework.api.StorageConfig;
import com.mobgen.halo.android.framework.common.annotations.Api;
import com.mobgen.halo.android.framework.common.helpers.subscription.ISubscription;
import com.mobgen.halo.android.framework.toolbox.bus.Event;
import com.mobgen.halo.android.framework.toolbox.bus.EventId;
import com.mobgen.halo.android.framework.toolbox.bus.Subscriber;
import com.mobgen.halo.android.framework.toolbox.data.CallbackV2;
import com.mobgen.halo.android.framework.toolbox.data.Data;
import com.mobgen.halo.android.framework.toolbox.data.HaloResultV2;
import com.mobgen.halo.android.framework.toolbox.scheduler.Job;
import com.mobgen.halo.android.framework.toolbox.threading.Threading;
import com.mobgen.halo.android.sdk.api.Halo;
import com.mobgen.halo.android.sdk.api.HaloPluginApi;
import com.mobgen.halo.android.sdk.core.internal.storage.HaloManagerContract;
import com.mobgen.halo.android.sdk.core.internal.storage.HaloMigration2$0$0;
import com.mobgen.halo.android.sdk.core.management.authentication.RequestTokenInteractor;
import com.mobgen.halo.android.sdk.core.management.authentication.TokenRemoteDatasource;
import com.mobgen.halo.android.sdk.core.management.authentication.TokenRepository;
import com.mobgen.halo.android.sdk.core.management.device.AddDeviceTagInteractor;
import com.mobgen.halo.android.sdk.core.management.device.DeviceLocalDatasource;
import com.mobgen.halo.android.sdk.core.management.device.DeviceRemoteDatasource;
import com.mobgen.halo.android.sdk.core.management.device.DeviceRepository;
import com.mobgen.halo.android.sdk.core.management.device.FetchDeviceInteractor;
import com.mobgen.halo.android.sdk.core.management.device.RemoveDeviceTagInteractor;
import com.mobgen.halo.android.sdk.core.management.device.SendDeviceInteractor;
import com.mobgen.halo.android.sdk.core.management.device.SetNotificationTokenInteractor;
import com.mobgen.halo.android.sdk.core.management.device.SyncDeviceSegmentedInteractor;
import com.mobgen.halo.android.sdk.core.management.events.EventRemoteDatasource;
import com.mobgen.halo.android.sdk.core.management.events.EventRepository;
import com.mobgen.halo.android.sdk.core.management.events.SendTrackEventInteractor;
import com.mobgen.halo.android.sdk.core.management.models.Credentials;
import com.mobgen.halo.android.sdk.core.management.models.Device;
import com.mobgen.halo.android.sdk.core.management.models.HaloEvent;
import com.mobgen.halo.android.sdk.core.management.models.HaloModule;
import com.mobgen.halo.android.sdk.core.management.models.HaloModuleQuery;
import com.mobgen.halo.android.sdk.core.management.models.HaloServerVersion;
import com.mobgen.halo.android.sdk.core.management.models.Token;
import com.mobgen.halo.android.sdk.core.management.modules.Cursor2ModulesConverter;
import com.mobgen.halo.android.sdk.core.management.modules.ModulesLocalDatasource;
import com.mobgen.halo.android.sdk.core.management.modules.ModulesRemoteDatasource;
import com.mobgen.halo.android.sdk.core.management.modules.ModulesRepository;
import com.mobgen.halo.android.sdk.core.management.modules.RequestModulesInteractor;
import com.mobgen.halo.android.sdk.core.management.segmentation.HaloSegmentationTag;
import com.mobgen.halo.android.sdk.core.management.version.GetVersionInteractor;
import com.mobgen.halo.android.sdk.core.management.version.VersionRemoteDatasource;
import com.mobgen.halo.android.sdk.core.management.version.VersionRepository;
import com.mobgen.halo.android.sdk.core.selectors.HaloSelectorFactory;
import com.mobgen.halo.android.sdk.core.threading.HaloInteractorExecutor;
import com.mobgen.halo.android.sdk.core.threading.HaloSchedule;
import com.mobgen.halo.android.sdk.core.threading.ICancellable;

import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Management plugin to make requests that represents some management action like
 * the server or requesting modules.
 */
@Keep
public class HaloManagerApi extends HaloPluginApi {

    /**
     * Event id for the update device event.
     */
    public static final EventId DEVICE_UPDATE_EVENT_ID = EventId.create(":halo:device:update");

    /**
     * The storage api for the manager.
     */
    private HaloStorageApi mManagerStorage;
    /**
     * The modules repository.
     */
    private ModulesRepository mModulesRepository;
    /**
     * The version repository.
     */
    private VersionRepository mVersionRepository;
    /**
     * The token repository.
     */
    private TokenRepository mTokenRepository;
    /**
     * Syncs the device information.
     */
    private DeviceRepository mDeviceRepository;

    /**
     * Send analytic track event.
     */
    private EventRepository mEventRespository;

    /**
     * Constructor for the halo plugin.
     *
     * @param halo The halo instance.
     */
    private HaloManagerApi(@NonNull Halo halo) {
        super(halo);
        mManagerStorage = halo.framework().createStorage(StorageConfig.builder()
                .storageName(HaloManagerContract.HALO_MANAGER_STORAGE)
                .databaseVersion(HaloManagerContract.CURRENT_VERSION)
                .addMigrations(
                        new HaloMigration2$0$0()
                )
                .build()
        );
        mModulesRepository = new ModulesRepository(new ModulesRemoteDatasource(halo.framework().network()), new ModulesLocalDatasource(mManagerStorage));
        mVersionRepository = new VersionRepository(new VersionRemoteDatasource(halo.framework().network()));
        mTokenRepository = new TokenRepository(new TokenRemoteDatasource(halo.framework().network()));
        mDeviceRepository = new DeviceRepository(framework().parser(), new DeviceRemoteDatasource(halo.framework().network()), new DeviceLocalDatasource(mManagerStorage));
        mEventRespository = new EventRepository(new EventRemoteDatasource(halo.framework().network()));
    }

    /**
     * Provides the management plugin.
     *
     * @param halo The halo instance.
     * @return The plugin instance.
     */
    public static HaloManagerApi with(Halo halo) {
        return new HaloManagerApi(halo);
    }

    /**
     * Provides the current storage api
     *
     * @return Return storage api
     */
    @Keep
    @Api(2.0)
    @NonNull
    public HaloStorageApi storage() {
        return mManagerStorage;
    }

    /**
     * Provides the current modules.
     *
     * @param dataMode    Tell where the data will be taken from.
     * @param moduleQuery The module query with options to make the request.
     * @return Returns the selector to execute the action.
     */
    @Keep
    @NonNull
    @Api(2.0)
    @CheckResult(suggest = "You may want to call asContent() or asRaw() to get the information")
    public HaloSelectorFactory<List<HaloModule>, Cursor> getModules(@Data.Policy int dataMode, @NonNull HaloModuleQuery moduleQuery) {
        return new HaloSelectorFactory<>(
                halo(),
                new RequestModulesInteractor(mModulesRepository, moduleQuery),
                new Cursor2ModulesConverter(),
                null,
                dataMode,
                "Get modules request"
        );
    }

    /**
     * Prints the current modules with all the fields metadata. You can use this method to see in the
     * log the metadata of the module instances.
     */
    @Keep
    @NonNull
    @Api(2.3)
    public ICancellable printModulesMetaData() {
        //force request without cache and display true
        HaloModuleQuery haloModulePrintQuery = HaloModuleQuery.builder()
                .serverCache(0)
                .withFields(true)
                .build();

        return HaloManagerApi.with(Halo.instance())
                .getModuleWithMetaData(haloModulePrintQuery)
                .asContent()
                .threadPolicy(Threading.POOL_QUEUE_POLICY)
                .execute();
    }

    /**
     * Provides the current modules with all the fields metadata.
     *
     * @return Returns the selector to execute the action.
     */
    @NonNull
    @CheckResult(suggest = "You may want to call execute() to run the task")
    private HaloSelectorFactory<List<HaloModule>, Cursor> getModuleWithMetaData(@NonNull HaloModuleQuery moduleQuery) {
        return new HaloSelectorFactory<>(
                halo(),
                new RequestModulesInteractor(mModulesRepository, moduleQuery),
                new Cursor2ModulesConverter(),
                null,
                Data.NETWORK_ONLY,
                "Get modules request with metadata fields"
        );
    }

    /**
     * Provides the server version for this item. .This request is always online
     * and will fail without connection
     *
     * @return The server version.
     */
    @Keep
    @Api(2.0)
    @NonNull
    @CheckResult(suggest = "You may want to call execute() to run the task")
    public HaloInteractorExecutor<HaloServerVersion> getServerVersion() {
        return new HaloInteractorExecutor<>(
                halo(),
                "Get the server version",
                new GetVersionInteractor(mVersionRepository)
        );
    }

    /**
     * Request a token with the given credentials.
     *
     * @param credentials The credentials.
     * @return The action created.
     */
    @Keep
    @Api(2.0)
    @NonNull
    @CheckResult(suggest = "You may want to call execute() to run the task")
    public HaloInteractorExecutor<Token> requestToken(@NonNull Credentials credentials) {
        return new HaloInteractorExecutor<>(
                halo(),
                "Request a new token",
                new RequestTokenInteractor(mTokenRepository, credentials)
        );
    }

    /**
     * Tells if the authentication is an app based one with client id and client secret.
     *
     * @return True if it is an app authentication.
     */
    @Keep
    @Api(1.3)
    public boolean isAppAuthentication() {
        Credentials credentials = core().credentials();
        return credentials.getLoginType() == Credentials.CLIENT_BASED_LOGIN;
    }

    /**
     * Tells if the authentication is based on a password and a username.
     *
     * @return True if it is a password based authentication.
     */
    @Keep
    @Api(1.3)
    public boolean isPasswordAuthentication() {
        Credentials credentials = core().credentials();
        return credentials.getLoginType() == Credentials.USER_BASED_LOGIN;
    }

    /**
     * Synchronizes the device stored with the one in the server.
     *
     * @return The executor.
     */
    @Keep
    @Api(2.0)
    @NonNull
    @CheckResult(suggest = "You may want to call execute() to run the task")
    public HaloInteractorExecutor<Device> syncDevice() {
        return new HaloInteractorExecutor<>(
                halo(),
                "Sync device",
                new SyncDeviceSegmentedInteractor(mDeviceRepository, halo().getCore())
        );
    }

    /**
     * Synchronizes the device stored with the one in the server once the network is available.
     * In this case callback is not allowed.
     */
    @Keep
    @Api(2.0)
    public void syncDeviceWhenNetworkAvailable(@Threading.Policy final int threading) {
        framework().toolbox().schedule(Job.builder(new HaloSchedule(halo()) {
            @Override
            public void executeWhenReady() {
                syncDevice().threadPolicy(threading).execute();
            }
        }).needsNetwork(Job.NETWORK_TYPE_ANY)
                .thread(threading)
                .tag("syncDevice")
                .build());
    }

    /**
     * Subscribes for the device update.
     *
     * @param subscriber The subscriber to receive the notification.
     * @return The subscription created.
     */
    @Keep
    @NonNull
    @Api(2.0)
    @CheckResult(suggest = "Subscription.unsubscribe() to avoid memory leaks")
    public ISubscription subscribeForDeviceSync(@NonNull Subscriber subscriber) {
        return framework().toolbox().eventHub().subscribe(subscriber, DEVICE_UPDATE_EVENT_ID);
    }

    /**
     * Updates the device that is present in the core sending it to the server.
     *
     * @return The current executor to perform the operation.
     */
    @Keep
    @Api(2.0)
    @NonNull
    @CheckResult(suggest = "You may want to call execute() to run the task")
    public HaloInteractorExecutor<Device> sendDevice() {
        return new HaloInteractorExecutor<>(
                halo(),
                "Update current device",
                new SendDeviceInteractor(mDeviceRepository)
        );
    }

    /**
     * Sets the notification token into the device.
     *
     * @return The notification token to be set.
     */
    @Keep
    @Api(2.0)
    @NonNull
    @CheckResult(suggest = "You may want to call execute() to run the task")
    public HaloInteractorExecutor<Device> setNotificationsToken(@Nullable String notificationToken) {
        return new HaloInteractorExecutor<>(
                halo(),
                "Set notification token and send device. Token: " + notificationToken,
                new SetNotificationTokenInteractor(mDeviceRepository, notificationToken)
        );
    }

    /**
     * Provides the current cached device.
     *
     * @return The current device.
     */
    @Keep
    @Api(2.0)
    @NonNull
    @CheckResult(suggest = "You may want to call execute() to run the task")
    public HaloInteractorExecutor<Device> fetchCurrentDevice() {
        return new HaloInteractorExecutor<>(
                halo(),
                "Fetches the current device into core",
                new FetchDeviceInteractor(mDeviceRepository)
        );
    }

    /**
     * Adds a segmentation tag to the current device or replaces the current one if there is another with the same
     * name.
     *
     * @param tag              The tag that will be added.
     * @param shouldSendDevice Provides if the device should be sent.
     * @return The action generated.
     */
    @Keep
    @Api(2.0)
    @NonNull
    @CheckResult(suggest = "You may want to call execute() to run the task")
    public HaloInteractorExecutor<Device> addDeviceTag(@Nullable HaloSegmentationTag tag, boolean shouldSendDevice) {
        return addDeviceTags(Collections.singletonList(tag), shouldSendDevice);
    }

    /**
     * Adds a segmentation tag to the current device or replaces the current one if there is another with the same
     * name.
     *
     * @param tag              The tag that will be added.
     * @param shouldSendDevice Provides if the device should be sent.
     * @return The action generated.
     */
    @Keep
    @Api(2.33)
    @NonNull
    @CheckResult(suggest = "You may want to call execute() to run the task")
    public HaloInteractorExecutor<Device> addDeviceTag(@Nullable HaloSegmentationTag tag, boolean shouldSendDevice, boolean shouldOverrideTags) {
        return addDeviceTags(Collections.singletonList(tag), shouldSendDevice, shouldOverrideTags);
    }

    /**
     * The segmentation tags.
     *
     * @param segmentationTags The tags.
     * @param shouldSendDevice Provides if the device should be sent.
     * @return The action generated.
     */
    @Keep
    @Api(2.0)
    @NonNull
    @CheckResult(suggest = "You may want to call execute() to run the task")
    public HaloInteractorExecutor<Device> addDeviceTags(@Nullable List<HaloSegmentationTag> segmentationTags, boolean shouldSendDevice) {
        return new HaloInteractorExecutor<>(halo(),
                "Add device tags",
                new AddDeviceTagInteractor(mDeviceRepository, segmentationTags, shouldSendDevice, true)
        );
    }

    /**
     * The segmentation tags.
     *
     * @param segmentationTags The tags.
     * @param shouldSendDevice Provides if the device should be sent.
     * @param shouldOverrideTags True if we will override every tag to have only one; otherwise false.
     * @return The action generated.
     */
    @Keep
    @Api(2.33)
    @NonNull
    @CheckResult(suggest = "You may want to call execute() to run the task")
    public HaloInteractorExecutor<Device> addDeviceTags(@Nullable List<HaloSegmentationTag> segmentationTags, boolean shouldSendDevice, boolean shouldOverrideTags) {
        return new HaloInteractorExecutor<>(halo(),
                "Add device tags",
                new AddDeviceTagInteractor(mDeviceRepository, segmentationTags, shouldSendDevice, shouldOverrideTags)
        );
    }

    /**
     * Removes the segmentation tag and updates the values.
     *
     * @param tagName          The tag name to remove.
     * @param shouldSendDevice Provides if the device should be sent.
     * @return The action generated.
     */
    @Keep
    @Api(2.0)
    @NonNull
    @CheckResult(suggest = "You may want to call execute() to run the task")
    public HaloInteractorExecutor<Device> removeDeviceTag(@Nullable String tagName, boolean shouldSendDevice) {
        return removeDeviceTags(Collections.singletonList(tagName), shouldSendDevice);
    }

    /**
     * Removes the segmentation tag and updates the values.
     *
     * @param tagNames         The tag name to remove.
     * @param shouldSendDevice Provides if the device should be sent.
     * @return The executor to sync the user.
     */
    @Keep
    @Api(2.0)
    @NonNull
    @CheckResult(suggest = "You may want to call execute() to run the task")
    public HaloInteractorExecutor<Device> removeDeviceTags(@Nullable List<String> tagNames, boolean shouldSendDevice) {
        return new HaloInteractorExecutor<>(
                halo(),
                "Remove device tags",
                new RemoveDeviceTagInteractor(mDeviceRepository, tagNames, shouldSendDevice)
        );
    }

    /**
     * Provides the current device. This can be null if the device object is not initialized. The device object is considered as initialized once
     * HALO is ready.
     *
     * @return The device.
     */
    @Keep
    @Api(2.0)
    @Nullable
    public Device getDevice() {
        return mDeviceRepository.getDeviceInMemory();
    }

    /**
     * Provides the current app id. This can be null if halo is not initialized yet.
     *
     * @return The application id info.
     */
    @Keep
    @Api(2.3)
    @Nullable
    public String getAppId() {
        if (mDeviceRepository.getDeviceInMemory() != null) {
            return mDeviceRepository.getDeviceInMemory().getAppId();
        } else {
            return null;
        }
    }

    /**
     * Send tracking analytic events of the current user.
     *
     * @return The executor.
     */
    @Keep
    @Api(2.33)
    @NonNull
    @CheckResult(suggest = "You may want to call execute() to run the task")
    public HaloInteractorExecutor<HaloEvent> sendEvent(@NonNull HaloEvent haloEvent) {
        return new HaloInteractorExecutor<>(
                halo(),
                "Send events",
                new SendTrackEventInteractor(mEventRespository, haloEvent)
        );
    }
}
