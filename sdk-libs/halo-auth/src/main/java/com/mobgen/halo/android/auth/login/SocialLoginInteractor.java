package com.mobgen.halo.android.auth.login;

import android.support.annotation.NonNull;

import com.mobgen.halo.android.framework.toolbox.data.HaloResultV2;
import com.mobgen.halo.android.sdk.api.Halo;
import com.mobgen.halo.android.sdk.core.threading.HaloInteractorExecutor;
import com.mobgen.halo.android.auth.HaloAuthApi;
import com.mobgen.halo.android.auth.authenticator.AccountManagerHelper;
import com.mobgen.halo.android.auth.models.IdentifiedUser;
import com.mobgen.halo.android.auth.providers.facebook.FacebookSocialProvider;
import com.mobgen.halo.android.auth.providers.google.GoogleSocialProvider;
import com.mobgen.halo.android.sdk.core.management.models.Session;

import static com.mobgen.halo.android.sdk.core.management.authentication.HaloAuthenticator.HALO_SESSION_NAME;


/**
 * Login with different network providers.
 */
public class SocialLoginInteractor implements HaloInteractorExecutor.Interactor<IdentifiedUser> {

    /**
     * The login repository.
     */
    private LoginRepository mLoginRepository;
    /**
     * The social network provider name.
     */
    private String mSocialProviderName;
    /**
     * The social network token.
     */
    private String mSocialToken;
    /**
     * The device alais
     */
    private String mDeviceAlias;
    /**
     * The account type.
     */
    private String mAccountType;
    /**
     * The recovery from account manager policy
     */
    private int mRecoveryPolicy;

    /**
     * Constructor for the interactor.
     *
     * @param accountType The account type.
     * @param loginRepository      The login repository.
     * @param socialApiName        The social api name.
     * @param socialToken          The social network token.
     * @param deviceAlias          The device alias.
     */
    public SocialLoginInteractor(String accountType, LoginRepository loginRepository, String socialApiName, String socialToken, String deviceAlias, int recoveryPolicy) {
        mAccountType = accountType;
        mLoginRepository = loginRepository;
        mSocialProviderName = socialApiName;
        mSocialToken = socialToken;
        mDeviceAlias = deviceAlias;
        mRecoveryPolicy = recoveryPolicy;
    }


    @NonNull
    @Override
    public HaloResultV2<IdentifiedUser> executeInteractor() throws Exception {
        HaloResultV2<IdentifiedUser> response = mLoginRepository.loginSocialProvider(mSocialProviderName, mSocialToken, mDeviceAlias);
        //store user credentials on account manager
        if (mRecoveryPolicy == HaloAuthApi.RECOVERY_ALWAYS && response.status().isOk()) {
            if (mAccountType != null) {
                AccountManagerHelper accountManagerHelper = new AccountManagerHelper(Halo.instance().context(), mAccountType);
                if (mSocialProviderName.equals(FacebookSocialProvider.SOCIAL_FACEBOOK_NAME)) {
                    accountManagerHelper.addAccountToken(AccountManagerHelper.FACEBOOK_AUTH_PROVIDER, response.data().getUser().getEmail(), mSocialToken, response.data().getToken());
                } else if (mSocialProviderName.equals(GoogleSocialProvider.SOCIAL_GOOGLE_NAME)) {
                    accountManagerHelper.addAccountToken(AccountManagerHelper.GOOGLE_AUTH_PROVIDER, response.data().getUser().getEmail(), mSocialToken, response.data().getToken());
                }
                //save session
                Session session = new Session(response.data().getToken());
                Halo.core().sessionManager().setSession(HALO_SESSION_NAME, session);
                Halo.instance().getCore().haloAuthRecover().recoverStatus(false);
            }
        }
        return response;

    }
}
