package io.mapwize.mapwizeuicomponents;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.util.Log;

import com.lemmingapex.trilateration.NonLinearLeastSquaresSolver;
import com.lemmingapex.trilateration.TrilaterationFunction;

import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer;
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import io.indoorlocation.core.IndoorLocation;
import io.indoorlocation.core.IndoorLocationProvider;
import io.indoorlocation.core.IndoorLocationProviderListener;

public class WifiIndoorLocationProvider extends IndoorLocationProvider implements IndoorLocationProviderListener {

    private ArrayList<RouterStored> routers;
    private boolean isStarted = false;
    private IndoorLocation currentLocation;

    // Constructor
    public WifiIndoorLocationProvider(Context context) {
        super();
        // Get the list of all routers stored in the JSON file (downloaded from Mapwize) :
        // curl --location --request GET "https://api.mapwize.io/v1/beacons?api_key=4e2f8bdd141a47247fa6de2be677f711&isPublished=all" > wifi_routers.json
        routers = getStoredRouters(context);
    }


    public IndoorLocation getCurrentLocation(){
        return currentLocation;
    }


    /** Manually set location, for debugging purposes */
    public void setIndoorLocation(IndoorLocation indoorLocation) {
        dispatchIndoorLocationChange(indoorLocation);
    }

    /** The main part of the program. Call this to do everything needed to find and update your current location */
    public void UpdateLocation(ArrayList<RouterScanned> routersScanned){
        /*
         * 1) [X] Get the whole JSON File from Mapwize (which contains the location/MAC Addresses of all Routers)
         * 2) [X] (Create and) Call the function to scan Wifi routers. [->Or maybe do that before hand and have that function call this one, passing an arraylist as a parameter]
         * 3) [X] Find and implement a function to Trilaterate position.
         * 4) [X] Once the position is found, save it in the 'currentLocation' variable
         * 5) [X] Call dispatchIndoorLocationChange(currentLocation)
         */


        /** Print scanned routers, for debugging */
        for (RouterScanned router : routersScanned){
            Log.i("SSID", router.SSID);
            Log.i("distance", String.valueOf(router.distance));
        }

        /** Create fake list of scanned routers to test while not in bits. */
        ArrayList<RouterScanned> routersScannedFake = new ArrayList<RouterScanned>()  {
            {
                add(new RouterScanned(10, "d4:6d:50:8a:72:9e")); //should be 10
                add(new RouterScanned(8, "lol:nope..."));//fake
                add(new RouterScanned(36, "nope:again:nope"));//fake
                add(new RouterScanned(5, "a0:ec:f9:b2:d3:de")); //should be 10
                add(new RouterScanned(10, "a0:ec:f9:65:b0:7e")); //should be 23
                add(new RouterScanned(72, "d4:6d:50:e7:fe:7e"));//new - 47
            }
        };

        /** Add fake list to real list simply to test and debug */
//        routersScanned.addAll(routersScannedFake);

        // Get the users current location            \\\|routersScannedFake|///  <- (For debugging purposes)
        IndoorLocation currentLocation = calculateLocation(routersScanned, routers, 4);
        if (currentLocation != null) {
            Log.e("CURRENT LOCATION!!! :", "Lat = " + currentLocation.getLatitude() + "Long = " + currentLocation.getLongitude());
            //this line automatically updates the location on the map
            dispatchIndoorLocationChange(currentLocation);
        }

    }


    /** Function that uses a Java library call to Trilaterate the users location. */
    private double[] trilaterateLocation (double[][] positions, double[] distances){

        // To make the distance calculation 3D (adding height) simply add another column to the position array. Height = floorNo. * 4.3 (or what ever the floor height of BITS is..)
        //Position array is a 2d array of all the (Utm Easting, Utm Northing, (height)} of each router in the vicinity. Here I'm only using the closest routers, not all of them. Using UTM insteasd of lat/long, since it provides location in meters, instead of degrees.
//        double[][] positions = new double[][] { { 5.0, -6.0 }, { 13.0, -15.0 }, { 21.0, -3.0 }, { 12.4, -21.2 } };
        //Distances is the distance to each respective router.
//        double[] distances = new double[] { 8.06, 13.97, 23.32, 15.31 };

        NonLinearLeastSquaresSolver solver = new NonLinearLeastSquaresSolver(new TrilaterationFunction(positions, distances), new LevenbergMarquardtOptimizer());
        LeastSquaresOptimizer.Optimum optimum = solver.solve();

        // the answer
        double[] centroid = optimum.getPoint().toArray();

        // error and geometry information; may throw SingularMatrixException depending the threshold argument provided
        RealVector standardDeviation = optimum.getSigma(0);
        RealMatrix covarianceMatrix = optimum.getCovariances(0);

        return centroid;
    }


    /**For every scanned router available(in order of best signal strength), check if its mac address matches one in the list.
     * If it matches, proceed. Else try the next closest router.
     * Keep trying until "noOfRouters" routers is reached, or the list of scanned routers is exhausted.
     */
    private IndoorLocation calculateLocation(ArrayList<RouterScanned> routersScanned, ArrayList<RouterStored> routersStored, int noOfRouters){

        int n=0;
        // Position & Distance variables to store each of the closest routers.
        //To change to 3D (add height), change this 2 -⤵ to a 3
        double[][] positions = new double[noOfRouters][2];
        double[]   distances = new double[noOfRouters];
        // List of the floor no (used as a map), each of those closest routers belongs to. The most frequent floor is assumed to be the floor we are on.
        int[] floor = new int[4];

        //For Every scanned router
        for (int i=0; i<routersScanned.size(); i++){
            String bssid = routersScanned.get(i).BSSID;
            double dist = routersScanned.get(i).distance;
            //Check each stored router (from JSON) for matching mac address
            for (int j=0; j<routersStored.size(); j++){
                //Store the latitude, longitude, and distance of that router, if match found.
                if(routersStored.get(j).mac.equals(bssid)){
                    //(To change to 3d add another dimension position[n][2] that uses the height estimated from the floor value.)
                    //Here I'm converting lat/long to UTM system (easting/northing) that uses a coordinate system in Meters. This is necessary since the distance to the router is in meters, and a standard unit of measurement in required for trilateration
                    positions[n][0] = routersStored.get(j).easting;  //x-axis
                    positions[n][1] = routersStored.get(j).northing; //y-axis
                    distances[n] = dist;
                    floor[routersStored.get(j).floor] += 1;
                    n++; //Incrementing n only if match is found. Keeps count of all matched routers.
                }
                //Else check the next router in the stored list
            }
            //Stop if you have found 'noOfRouter' matches (Upper limit)
            if(n==noOfRouters){
                break;
            }
        }

        /** Tried to be smart... Didn't work :( You'll have to readjust the size of the array, to make it work
         *  For now if using the commented part, the exact 'noOfRouters' specified should be found among the scan list, or it freaks out.
         *  Switching back to the safe way of doings things. Either 'noOfRouters' matches are found, or the location is not updated...*/
        // IF we could not find atleast 3 routers that matched Mac addresses, simply return null
        // This causes the function to us a maximum of 'noOfRouters' matches to calculate location (if avaliable), and minimum 3 matches.
//      if(n<3){
        if(n<noOfRouters){
            return null;
        }

        //Find the floor you are currently on.
        int max_floor = 0;
        int max_val = 0;

        //assume you are on the same floor as the max no of scanned routers.
        for (int i=0; i<noOfRouters; i++){
            if(floor[i]>max_val){
                max_val = floor[i];
                max_floor = i;
            }
        }

        //Apply trilateration to get the users current location, given router locations and distances to them (result in UTM since input is in UTM).
        double[] centroid = trilaterateLocation(positions, distances);
        //Converting UTM location back to Lat/Long location, so it can be passed to mapwize to display the position on the map.
        double[] location = utm2LatLong(routersStored.get(0).zone, routersStored.get(0).letter, centroid[0], centroid[1]);
        //convert lat/long to "IndoorLocation" class so map position can be updated.
        IndoorLocation currentLocation = new IndoorLocation(this.getName(), location[0], location[1], (double) max_floor, System.currentTimeMillis());

        return currentLocation;

    }


    /** Scan the JSON file to get the list of all routers with their mac addresses and lat/long locaions */
    private static ArrayList<RouterStored> getStoredRouters(Context context) {

        ArrayList<RouterStored> routers = new ArrayList<RouterStored>();
        try {
            JSONArray obj = new JSONArray(loadJSONFromAsset(context));
//            Log.i("json", String.valueOf(obj));

            for (int i = 0; i < obj.length(); i++) {
                JSONObject routerDetails = obj.getJSONObject(i);

                double lat = routerDetails.getJSONObject("location").getDouble("lat");
                double lon = routerDetails.getJSONObject("location").getDouble("lon");
                String mac = routerDetails.getJSONObject("properties").getString("mac");
                int  floor = routerDetails.getInt("floor");

                RouterStored r = new RouterStored(lat, lon, mac, floor);
                routers.add(r);
            }


        } catch (Exception e) {
            e.printStackTrace();
        }

        return routers;

    }

    /** Helper function to convert json file to string (necessary for it to be parsed) */
    private static String loadJSONFromAsset(Context context) {
        String json;
        try {
            InputStream is = context.getAssets().open("wifi_routers.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, "UTF-8");
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
//        Log.e("data", json);
        return json;
    }


    /** Convert UTM distance to Latitude/Longitude Distance */
    private double[] utm2LatLong(int Zone, char Letter, double Easting, double Northing)
    {
//        String[] parts=UTM.split(" ");
//        int Zone=Integer.parseInt(parts[0]);
//        char Letter=parts[1].toUpperCase(Locale.ENGLISH).charAt(0);
//        double Easting=Double.parseDouble(parts[2]);
//        double Northing=Double.parseDouble(parts[3]);
        double Hem;
        double latitude;
        double longitude;
        if (Letter>'M')
            Hem='N';
        else
            Hem='S';
        double north;
        if (Hem == 'S')
            north = Northing - 10000000;
        else
            north = Northing;
        latitude = (north/6366197.724/0.9996+(1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2)-0.006739496742*Math.sin(north/6366197.724/0.9996)*Math.cos(north/6366197.724/0.9996)*(Math.atan(Math.cos(Math.atan(( Math.exp((Easting - 500000) / (0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2))))*(1-0.006739496742*Math.pow((Easting - 500000) / (0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2)))),2)/2*Math.pow(Math.cos(north/6366197.724/0.9996),2)/3))-Math.exp(-(Easting-500000)/(0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2))))*( 1 -  0.006739496742*Math.pow((Easting - 500000) / (0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2)))),2)/2*Math.pow(Math.cos(north/6366197.724/0.9996),2)/3)))/2/Math.cos((north-0.9996*6399593.625*(north/6366197.724/0.9996-0.006739496742*3/4*(north/6366197.724/0.9996+Math.sin(2*north/6366197.724/0.9996)/2)+Math.pow(0.006739496742*3/4,2)*5/3*(3*(north/6366197.724/0.9996+Math.sin(2*north/6366197.724/0.9996 )/2)+Math.sin(2*north/6366197.724/0.9996)*Math.pow(Math.cos(north/6366197.724/0.9996),2))/4-Math.pow(0.006739496742*3/4,3)*35/27*(5*(3*(north/6366197.724/0.9996+Math.sin(2*north/6366197.724/0.9996)/2)+Math.sin(2*north/6366197.724/0.9996)*Math.pow(Math.cos(north/6366197.724/0.9996),2))/4+Math.sin(2*north/6366197.724/0.9996)*Math.pow(Math.cos(north/6366197.724/0.9996),2)*Math.pow(Math.cos(north/6366197.724/0.9996),2))/3))/(0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2))))*(1-0.006739496742*Math.pow((Easting-500000)/(0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2)))),2)/2*Math.pow(Math.cos(north/6366197.724/0.9996),2))+north/6366197.724/0.9996)))*Math.tan((north-0.9996*6399593.625*(north/6366197.724/0.9996 - 0.006739496742*3/4*(north/6366197.724/0.9996+Math.sin(2*north/6366197.724/0.9996)/2)+Math.pow(0.006739496742*3/4,2)*5/3*(3*(north/6366197.724/0.9996+Math.sin(2*north/6366197.724/0.9996)/2)+Math.sin(2*north/6366197.724/0.9996 )*Math.pow(Math.cos(north/6366197.724/0.9996),2))/4-Math.pow(0.006739496742*3/4,3)*35/27*(5*(3*(north/6366197.724/0.9996+Math.sin(2*north/6366197.724/0.9996)/2)+Math.sin(2*north/6366197.724/0.9996)*Math.pow(Math.cos(north/6366197.724/0.9996),2))/4+Math.sin(2*north/6366197.724/0.9996)*Math.pow(Math.cos(north/6366197.724/0.9996),2)*Math.pow(Math.cos(north/6366197.724/0.9996),2))/3))/(0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2))))*(1-0.006739496742*Math.pow((Easting-500000)/(0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2)))),2)/2*Math.pow(Math.cos(north/6366197.724/0.9996),2))+north/6366197.724/0.9996))-north/6366197.724/0.9996)*3/2)*(Math.atan(Math.cos(Math.atan((Math.exp((Easting-500000)/(0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2))))*(1-0.006739496742*Math.pow((Easting-500000)/(0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2)))),2)/2*Math.pow(Math.cos(north/6366197.724/0.9996),2)/3))-Math.exp(-(Easting-500000)/(0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2))))*(1-0.006739496742*Math.pow((Easting-500000)/(0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2)))),2)/2*Math.pow(Math.cos(north/6366197.724/0.9996),2)/3)))/2/Math.cos((north-0.9996*6399593.625*(north/6366197.724/0.9996-0.006739496742*3/4*(north/6366197.724/0.9996+Math.sin(2*north/6366197.724/0.9996)/2)+Math.pow(0.006739496742*3/4,2)*5/3*(3*(north/6366197.724/0.9996+Math.sin(2*north/6366197.724/0.9996)/2)+Math.sin(2*north/6366197.724/0.9996)*Math.pow(Math.cos(north/6366197.724/0.9996),2))/4-Math.pow(0.006739496742*3/4,3)*35/27*(5*(3*(north/6366197.724/0.9996+Math.sin(2*north/6366197.724/0.9996)/2)+Math.sin(2*north/6366197.724/0.9996)*Math.pow(Math.cos(north/6366197.724/0.9996),2))/4+Math.sin(2*north/6366197.724/0.9996)*Math.pow(Math.cos(north/6366197.724/0.9996),2)*Math.pow(Math.cos(north/6366197.724/0.9996),2))/3))/(0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2))))*(1-0.006739496742*Math.pow((Easting-500000)/(0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2)))),2)/2*Math.pow(Math.cos(north/6366197.724/0.9996),2))+north/6366197.724/0.9996)))*Math.tan((north-0.9996*6399593.625*(north/6366197.724/0.9996-0.006739496742*3/4*(north/6366197.724/0.9996+Math.sin(2*north/6366197.724/0.9996)/2)+Math.pow(0.006739496742*3/4,2)*5/3*(3*(north/6366197.724/0.9996+Math.sin(2*north/6366197.724/0.9996)/2)+Math.sin(2*north/6366197.724/0.9996)*Math.pow(Math.cos(north/6366197.724/0.9996),2))/4-Math.pow(0.006739496742*3/4,3)*35/27*(5*(3*(north/6366197.724/0.9996+Math.sin(2*north/6366197.724/0.9996)/2)+Math.sin(2*north/6366197.724/0.9996)*Math.pow(Math.cos(north/6366197.724/0.9996),2))/4+Math.sin(2*north/6366197.724/0.9996)*Math.pow(Math.cos(north/6366197.724/0.9996),2)*Math.pow(Math.cos(north/6366197.724/0.9996),2))/3))/(0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2))))*(1-0.006739496742*Math.pow((Easting-500000)/(0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2)))),2)/2*Math.pow(Math.cos(north/6366197.724/0.9996),2))+north/6366197.724/0.9996))-north/6366197.724/0.9996))*180/Math.PI;
        latitude=Math.round(latitude*10000000);
        latitude=latitude/10000000;
        longitude =Math.atan((Math.exp((Easting-500000)/(0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2))))*(1-0.006739496742*Math.pow((Easting-500000)/(0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2)))),2)/2*Math.pow(Math.cos(north/6366197.724/0.9996),2)/3))-Math.exp(-(Easting-500000)/(0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2))))*(1-0.006739496742*Math.pow((Easting-500000)/(0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2)))),2)/2*Math.pow(Math.cos(north/6366197.724/0.9996),2)/3)))/2/Math.cos((north-0.9996*6399593.625*( north/6366197.724/0.9996-0.006739496742*3/4*(north/6366197.724/0.9996+Math.sin(2*north/6366197.724/0.9996)/2)+Math.pow(0.006739496742*3/4,2)*5/3*(3*(north/6366197.724/0.9996+Math.sin(2*north/6366197.724/0.9996)/2)+Math.sin(2* north/6366197.724/0.9996)*Math.pow(Math.cos(north/6366197.724/0.9996),2))/4-Math.pow(0.006739496742*3/4,3)*35/27*(5*(3*(north/6366197.724/0.9996+Math.sin(2*north/6366197.724/0.9996)/2)+Math.sin(2*north/6366197.724/0.9996)*Math.pow(Math.cos(north/6366197.724/0.9996),2))/4+Math.sin(2*north/6366197.724/0.9996)*Math.pow(Math.cos(north/6366197.724/0.9996),2)*Math.pow(Math.cos(north/6366197.724/0.9996),2))/3)) / (0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2))))*(1-0.006739496742*Math.pow((Easting-500000)/(0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2)))),2)/2*Math.pow(Math.cos(north/6366197.724/0.9996),2))+north/6366197.724/0.9996))*180/Math.PI+Zone*6-183;
        longitude=Math.round(longitude*10000000);
        longitude=longitude/10000000;

        double ret[] = new double[] {latitude, longitude};
        return ret;
    }


    /**
     * IndoorLocationProvider functions
     */

    @Override
    public boolean supportsFloor() {
        return true;
    }

    @Override
    public void start() {
        this.isStarted = true;
    }

    @Override
    public void stop() {
        this.isStarted = false;
    }

    @Override
    public boolean isStarted() {
        return this.isStarted;
    }


    /**
     * IndoorLocationProviderListener functions
     */

    @Override
    public void onProviderStarted() {
        this.dispatchOnProviderStarted();
    }

    @Override
    public void onProviderStopped() {
        this.dispatchOnProviderStopped();
    }

    @Override
    public void onProviderError(Error error) {
        dispatchOnProviderError(error);
    }

    @Override
    public void onIndoorLocationChange(IndoorLocation indoorLocation) {
        dispatchIndoorLocationChange(indoorLocation);
//        getLastLocation()
        currentLocation = indoorLocation;
    }


}
