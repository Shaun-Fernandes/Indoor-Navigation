package io.mapwize.mapwizeuicomponents


import android.app.Application
import io.mapwize.mapwizesdk.core.MapwizeConfiguration

class MapwizeApplication: Application() {

    override fun onCreate() {
        super.onCreate()
//        val config = MapwizeConfiguration.Builder(this, "2f4c9e64da9f2d496742089ce36eebca").build()
        val config = MapwizeConfiguration.Builder(this, "4e2f8bdd141a47247fa6de2be677f711").build()
        MapwizeConfiguration.start(config)
    }

}