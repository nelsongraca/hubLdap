package com.flowkode.hubldap;

import com.flowkode.hubldap.data.AuthResponse;
import com.flowkode.hubldap.data.UserGroupsResponse;
import com.flowkode.hubldap.data.UsersResponse;
import retrofit2.Call;
import retrofit2.http.*;

public interface HubClient {

    @GET("usergroups")
    Call<UserGroupsResponse> getUserGroups(@Header("Authorization") String authorization, @Query("$skip") int start, @Query("$top") int limit);

    @GET("users")
    Call<UsersResponse> getUsers(@Header("Authorization") String authorization, @Query("$skip") int start, @Query("$top") int limit);

    //    @POST("oauth2/token")
//    Call<PagedResponse<UserGroup>> userLogin(@Header("Authorization") String authorization);

    @POST("oauth2/token")
    @FormUrlEncoded
    Call<AuthResponse> serviceLogin(@Header("Authorization") String credentials, @Field("scope") String scope, @Field("grant_type") String grantType);

    default Call<AuthResponse> serviceLogin(@Header("Authorization") String credentials) {
        return serviceLogin(credentials, "0-0-0-0-0", "client_credentials");
    }


}
