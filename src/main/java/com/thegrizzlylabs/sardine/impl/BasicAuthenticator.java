package com.thegrizzlylabs.sardine.impl;

import java.io.IOException;

import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

import org.jetbrains.annotations.NotNull;

/**
 * Created by guillaume on 20/11/2017.
 */
class BasicAuthenticator implements Authenticator {

    private String userName;
    private String password;

    public BasicAuthenticator(@NotNull String userName, @NotNull String password) {
        this.userName = userName;
        this.password = password;
    }

    @Override
    public Request authenticate(Route route, Response response) throws IOException {
        if (response.request().header("Authorization") != null) {
            return null; // Give up, we've already attempted to authenticate.
        }

        System.out.println("Authenticating for response: " + response);
        System.out.println("Challenges: " + response.challenges());
        String credential = Credentials.basic(userName, password);
        return response.request().newBuilder()
                .header("Authorization", credential)
                .build();
    }
}
