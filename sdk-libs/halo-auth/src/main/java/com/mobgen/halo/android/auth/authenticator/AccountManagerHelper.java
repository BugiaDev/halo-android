package com.mobgen.halo.android.auth.authenticator;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.mobgen.halo.android.framework.common.exceptions.HaloParsingException;
import com.mobgen.halo.android.framework.common.utils.AssertionUtils;
import com.mobgen.halo.android.auth.models.HaloAuthProfile;
import com.mobgen.halo.android.sdk.api.Halo;
import com.mobgen.halo.android.sdk.core.management.models.Token;

import java.util.Arrays;
import java.util.List;


/**
 * The helper to work with account manager
 */
public class AccountManagerHelper {
    /**
     * Halo type token
     */
    public static final String HALO_AUTH_PROVIDER = "halo";
    /**
     * Facebook type token
     */
    public static final String FACEBOOK_AUTH_PROVIDER = "facebook";
    /**
     * Google token type
     */
    public static final String GOOGLE_AUTH_PROVIDER = "google";
    /**
     * The token type to store on account manager
     */
    private static String TOKEN_PROVIDER_TYPE = "token_provider_type";
    /**
     * The account type.
     */
    private String mAccountType;
    /**
     * The account manager.
     */
    private AccountManager mAccountManager;

    /**
     * Constructor
     *
     * @param context The Context.
     */
    public AccountManagerHelper(@NonNull Context context, String accountType) {
        mAccountManager = AccountManager.get(context);
        mAccountType = accountType;
    }

    /**
     * Set the token to an account
     *
     * @param account       The account to store on account manager.
     * @param tokenProvider The token provider type.
     * @param token         The token generated.
     * @return The account  with the token.
     */
    @NonNull
    public Account setAuthToken(@NonNull Account account, @NonNull String tokenProvider, @NonNull String token) {
        AssertionUtils.notNull(account, "account");
        AssertionUtils.notNull(tokenProvider, "tokenProvider");
        AssertionUtils.notNull(token, "token");
        mAccountManager.setAuthToken(account, tokenProvider, token);
        return account;
    }

    /**
     * Add or update account on the account manager. This is for user password authentication
     *
     * @param tokenProvider   The token provider type.
     * @param userName        The username of the user.
     * @param password        The password of the user.
     * @param haloAccessToken The halo access token.
     * @return Return the account added.
     */
    @Nullable
    public Account addAccountWithPassword(@NonNull String tokenProvider, @NonNull String userName, @NonNull String password, @NonNull Token haloAccessToken) throws HaloParsingException {
        AssertionUtils.notNull(tokenProvider, "tokenProvider");
        AssertionUtils.notNull(userName, "userName");
        AssertionUtils.notNull(password, "password");
        Account account = new Account(userName, mAccountType);
        if (checkAccountExist(account)) {
            mAccountManager.setPassword(account, password);
            mAccountManager.setUserData(account, tokenProvider, Token.serialize(haloAccessToken, Halo.instance().framework().parser()));
            mAccountManager.setUserData(account, TOKEN_PROVIDER_TYPE, tokenProvider);
            return account;
        } else {
            deleteOtherAccounts(account);
            Bundle extraData = new Bundle();
            extraData.putString(tokenProvider, Token.serialize(haloAccessToken, Halo.instance().framework().parser()));
            extraData.putString(TOKEN_PROVIDER_TYPE, tokenProvider);
            if (mAccountManager.addAccountExplicitly(setAuthToken(account, tokenProvider, haloAccessToken.toString()), password, extraData)) {
                return account;
            }
        }
        return null;
    }

    /**
     * Add or update account on the account manager. This is for user with no password authentication
     *
     * @param tokenProvider   The token provider type.
     * @param userName        The username of the user.
     * @param socialAuthToken The social provider token.
     * @param haloAccessToken The halo access token.
     * @return Return the account added.
     */
    @Nullable
    public Account addAccountToken(@NonNull String tokenProvider, @NonNull String userName, @NonNull String socialAuthToken, @NonNull Token haloAccessToken) throws HaloParsingException {
        AssertionUtils.notNull(tokenProvider, "tokenProvider");
        AssertionUtils.notNull(userName, "userName");
        AssertionUtils.notNull(haloAccessToken, "haloAccessToken");
        Account account = new Account(userName, mAccountType);
        if (checkAccountExist(account)) {
            mAccountManager.setUserData(account, tokenProvider, socialAuthToken);
            //if we have a halo credential on the account we will restore with username/password
            if (mAccountManager.getPassword(account) == null) {
                mAccountManager.setUserData(account, TOKEN_PROVIDER_TYPE, tokenProvider);
            }
            mAccountManager.setUserData(account, HALO_AUTH_PROVIDER, Token.serialize(haloAccessToken, Halo.instance().framework().parser()));
            return setAuthToken(account, tokenProvider, socialAuthToken);
        } else {
            deleteOtherAccounts(account);
            Bundle extraData = new Bundle();
            extraData.putString(tokenProvider, socialAuthToken);
            extraData.putString(TOKEN_PROVIDER_TYPE, tokenProvider);
            extraData.putString(HALO_AUTH_PROVIDER, Token.serialize(haloAccessToken, Halo.instance().framework().parser()));
            if (mAccountManager.addAccountExplicitly(setAuthToken(account, tokenProvider, socialAuthToken), null, extraData)) {
                return account;
            }
        }
        return null;
    }


    /**
     * Remove account because user logout
     *
     * @param account The account.
     */
    public void removeAccount(@NonNull Account account) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            mAccountManager.removeAccountExplicitly(account);
        } else {
            mAccountManager.removeAccount(account, null, null);
        }
    }

    /**
     * Gets the account manager token type stored for this account
     *
     * @param account The account.
     * @return Return the social token.
     */
    @Nullable
    public String getTokenProvider(@Nullable Account account) {
        if (account != null) {
            return mAccountManager.getUserData(account, TOKEN_PROVIDER_TYPE);
        }
        return null;
    }

    /**
     * Gets the account stored based on account type and token type. There is only one account on account manager
     *
     * @return Return the account stored.
     */
    @Nullable
    public Account recoverAccount() {
        try {
            List<Account> accounts = Arrays.asList(mAccountManager.getAccountsByType(mAccountType));
            if (accounts.size() > 0) {
                return accounts.get(0);
            } else {
                return null;
            }
        }  catch (SecurityException securityException){
            return null;
        }
    }

    /**
     * Gets the HaloAuthProfile stored
     *
     * @param account The account.
     * @param alias   The alias of the device.
     * @return Return the authorization profile to make a halo login.
     */
    @Nullable
    public HaloAuthProfile getAuthProfile(@Nullable Account account, @NonNull String alias) {
        AssertionUtils.notNull(alias, "alias");
        if (account != null && mAccountManager.getPassword(account) != null) {
            return new HaloAuthProfile(account.name, mAccountManager.getPassword(account), alias);
        } else {
            return null;
        }
    }

    /**
     * Gets the auth token stored
     *
     * @param account   The account.
     * @param tokenType The token type.
     * @return Return the social token.
     */
    @Nullable
    public String getAuthToken(@Nullable Account account, @NonNull String tokenType) {
        AssertionUtils.notNull(tokenType, "tokenType");
        if (account != null) {
            return mAccountManager.getUserData(account, tokenType);
        }
        return null;
    }

    /**
     * Check if exist account on account manager
     *
     * @param account The account.
     * @return True if account is already authenticated, false otherwise.
     */
    private boolean checkAccountExist(@NonNull Account account) {
        AssertionUtils.notNull(account, "account");
        try {
            List<Account> accountList = Arrays.asList(mAccountManager.getAccountsByType(mAccountType));
            return accountList.contains(account);
        } catch(SecurityException securityException){
            return false;
        }
    }

    /**
     * Delete account with different account name on account manager to only manage one account per user
     *
     * @param account The account.
     * @return True if account is already authenticated, false otherwise.
     */
    @NonNull
    private void deleteOtherAccounts(@NonNull Account account) {
        AssertionUtils.notNull(account, "account");
        try {
            List<Account> accountList = Arrays.asList(mAccountManager.getAccountsByType(mAccountType));
            for (int i = 0; i < accountList.size(); i++) {
                if (accountList.get(i).name != account.name) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                        mAccountManager.removeAccountExplicitly(accountList.get(i));
                    } else {
                        mAccountManager.removeAccount(account, null, null);
                    }
                }
            }
        } catch(SecurityException securityException){}
    }
}
