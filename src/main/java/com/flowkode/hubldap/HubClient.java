package com.flowkode.hubldap;

import com.flowkode.hubldap.data.AuthResponse;
import com.flowkode.hubldap.data.User;
import com.flowkode.hubldap.data.UserGroupsResponse;
import com.flowkode.hubldap.data.UsersResponse;
import retrofit2.Call;
import retrofit2.http.*;

public interface HubClient {

    @GET("usergroups?fields=total,type,id,name")
    Call<UserGroupsResponse> getUserGroups(@Header("Authorization") String authorization, @Query("$skip") int start, @Query("$top") int limit);

    @GET("users")
    Call<UsersResponse> getUsers(@Header("Authorization") String authorization, @Query("$skip") int start, @Query("$top") int limit);

    @POST("oauth2/token")
    @FormUrlEncoded
    Call<AuthResponse> userLogin(@Header("Authorization") String authorization, @Field("scope") String scope, @Field("grant_type") String grantType, @Field("username") String username, @Field("password") String password);

    @POST("oauth2/token")
    @FormUrlEncoded
    Call<AuthResponse> serviceLogin(@Header("Authorization") String credentials, @Field("scope") String scope, @Field("grant_type") String grantType);

    @GET("users/{userId}")
    Call<User> getUser(@Header("Authorization") String credentials, @Path("userId") String userId);

    default Call<AuthResponse> userLogin(String authorization, String username, String password) {
        return userLogin(authorization, "0-0-0-0-0", "password", username, password);
    }

    default Call<AuthResponse> serviceLogin(@Header("Authorization") String credentials) {
        return serviceLogin(credentials, "0-0-0-0-0", "client_credentials");
    }
}
