package org.namchieh.rusmorph.update.data

import org.namchieh.rusmorph.update.model.UpdateManifest
import retrofit2.http.GET

interface UpdateApi {
    @GET("update/android/stable")
    suspend fun stableAndroidUpdate(): UpdateManifest
}
