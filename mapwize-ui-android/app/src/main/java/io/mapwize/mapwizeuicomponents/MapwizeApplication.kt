package io.mapwize.mapwizeuicomponents


import android.app.Application
import io.mapwize.mapwizesdk.core.MapwizeConfiguration

class MapwizeApplication: Application() {

    override fun onCreate() {
        super.onCreate()
        val config = MapwizeConfiguration.Builder(this, "2f4c9e64da9f2d496742089ce36eebca").build()
        MapwizeConfiguration.start(config)
    }

}