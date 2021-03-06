package com.mobgen.locationpoc.ui;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.mobgen.halo.android.auth.HaloAuthApi;
import com.mobgen.halo.android.auth.models.HaloAuthProfile;
import com.mobgen.halo.android.auth.models.IdentifiedUser;
import com.mobgen.halo.android.auth.providers.SocialNotAvailableException;
import com.mobgen.halo.android.framework.toolbox.data.CallbackV2;
import com.mobgen.halo.android.framework.toolbox.data.HaloResultV2;
import com.mobgen.halo.android.sdk.api.Halo;
import com.mobgen.locationpoc.R;

/**
 * Created by f.souto.gonzalez on 07/06/2017.
 */

public class LoginActivity extends AppCompatActivity {

    public static final String USER_NAME = "userName";
    public static final String USER_MAIL = "userEmail";
    public static final String USER_PHOTO = "userPhoto";

    private EditText mEmailText;
    private EditText mPasswordText;
    private TextInputLayout mEmailLayout;
    private TextInputLayout mPasswordLayout;
    private Button mLoginButton;
    private TextView mSignupLink;
    private Context mContext;

    public static void start(@NonNull Context context) {
        Intent intent = new Intent(context, LoginActivity.class);
        context.startActivity(intent);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mContext = this;

        mEmailText = (EditText) findViewById(R.id.input_email);
        mPasswordText = (EditText) findViewById(R.id.input_password);
        mEmailLayout = (TextInputLayout) findViewById(R.id.input_layout_email);
        mPasswordLayout = (TextInputLayout) findViewById(R.id.input_layout_password);
        mLoginButton = (Button) findViewById(R.id.btn_login);
        mSignupLink = (TextView) findViewById(R.id.link_signup);

        //if we have a account stored continue to home screen
        if (MobgenHaloApplication.getAuth().isAccountStored()) {
            onLoginSuccess();
        }

        mLoginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                login();
            }
        });

        mSignupLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SignupActivity.start(mContext);
            }
        });
    }

    /**
     * Attempt to login
     */
    public void login() {
        if (!validate()) {
            onLoginFailed();
            return;
        }

        mLoginButton.setEnabled(false);
        final ProgressDialog progressDialog = new ProgressDialog(LoginActivity.this,
                R.style.AppTheme_PopupOverlay);
        progressDialog.setIndeterminate(true);
        progressDialog.setMessage(getString(R.string.login_attempt));
        progressDialog.show();

        String email = mEmailText.getText().toString();
        String password = mPasswordText.getText().toString();

        try {
            HaloAuthProfile auth = new HaloAuthProfile(email, password);
            MobgenHaloApplication.getAuth().loginWithHalo(HaloAuthApi.SOCIAL_HALO, auth, new CallbackV2<IdentifiedUser>() {
                @Override
                public void onFinish(@NonNull HaloResultV2<IdentifiedUser> result) {
                    progressDialog.dismiss();
                    if (result.data() != null) {
                        //save userName
                        String photoUrl = null;
                        if(!result.data().getUser().getPhoto().equals("")){
                            photoUrl = result.data().getUser().getPhoto();
                        }
                        MobgenHaloApplication.halo()
                                .getCore().manager().storage()
                                .prefs()
                                .edit()
                                .putString(USER_MAIL, result.data().getUser().getEmail())
                                .putString(USER_NAME, result.data().getUser().getName())
                                .putString(USER_PHOTO, photoUrl)
                                .apply();
                        onLoginSuccess();
                    } else {
                        onLoginFailed();
                    }
                }
            });
        } catch (SocialNotAvailableException e) {
        }
    }

    /**
     * Success with a login attempt
     */
    public void onLoginSuccess() {
        mLoginButton.setEnabled(true);
        HomeActivity.start(mContext);
        finish();
    }

    /**
     * Error with a login attempt
     */
    public void onLoginFailed() {
        Toast.makeText(getBaseContext(), getString(R.string.login_error), Toast.LENGTH_LONG).show();
        mLoginButton.setEnabled(true);
    }

    /**
     * Validate the fields of the form
     *
     * @return True if all its ok
     */
    public boolean validate() {
        boolean valid = true;

        String email = mEmailText.getText().toString();
        String password = mPasswordText.getText().toString();

        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            mEmailText.setError(getString(R.string.login_validate_email_error));
            valid = false;
        } else {
            mEmailText.setError(null);
        }

        if (password.isEmpty() || password.length() < 4 || password.length() > 20) {
            mPasswordText.setError(getString(R.string.login_validate_password_error));
            valid = false;
        } else {
            mPasswordText.setError(null);
        }

        mEmailLayout.setErrorEnabled(valid);
        mPasswordLayout.setErrorEnabled(valid);

        return valid;
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }
}
