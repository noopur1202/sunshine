package com.example.android.sunshine.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

/**
 * A placeholder fragment containing a simple view.
 */
public class ForecastFragment extends Fragment
{

    private ArrayAdapter<String> mForecastAdapter;

    public ForecastFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
    {
        inflater.inflate(R.menu.forecast,menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id=item.getItemId();
        if (id==R.id.action_refresh)
        {
            UpdateWeather();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {

        mForecastAdapter= new ArrayAdapter<String>(

                getActivity(),
                R.layout.list_item_forecast,
                R.id.list_item_forecast_textView,
                new ArrayList<String>());

        View rootView=inflater.inflate(R.layout.fragment_main,container,false);

        ListView listView= (ListView)rootView.findViewById(R.id.listView_forecast);
        listView.setAdapter(mForecastAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l)
            {
                String forecast=mForecastAdapter.getItem(position);
                Intent intent=new Intent(getActivity(),DetailActivity.class).putExtra(Intent.EXTRA_TEXT,forecast);
                startActivity(intent);
            }
        });

        return rootView;
    }

    private void UpdateWeather()
    {
        FetchWeatherTask weatherTask=new FetchWeatherTask();
        SharedPreferences pref= PreferenceManager.getDefaultSharedPreferences(getActivity());
        String location=pref.getString(getString(R.string.pref_location_key),getString(R.string.pref_location_default));
        weatherTask.execute(location);
    }

    @Override
    public void onStart() {
        super.onStart();
        UpdateWeather();
    }

    public class FetchWeatherTask extends AsyncTask<String, Void, String[]>
        {
            private final String LOG_TAG=FetchWeatherTask.class.getSimpleName();

            private String getReadableDateString(long time)
            {
                SimpleDateFormat shortenedDateFormat= new SimpleDateFormat("EEE MMM dd");
                return shortenedDateFormat.format(time);
            }

            private String formatHighLow(double high,double low,String unit_type)
            {
                if (unit_type.equals(getString(R.string.pref_units_imperial)))
                {
                    high=(high*1.8)+32;
                    low=(low*1.8)+32;
                }else if (!unit_type.equals(getString(R.string.pref_units_metric)))
                {
                    Log.d(LOG_TAG,"Unit type not found"+unit_type);

                }
                long roundedHigh=Math.round(high);
                long roundedLow=Math.round(low);

                String highLowString=roundedHigh+"/"+roundedLow;
                return highLowString;
            }

            private String[] getWeatherDataFromJson(String forecastJsonStr,int numDays)throws JSONException
            {
                final String OWM_LIST="list";
                final String OWM_WEATHER="weather";
                final String OWM_TEMPERATURE="temp";
                final String OWM_MAX="max";
                final String OWM_MIN="min";
                final String OWM_DESCRIPTION="description";

                JSONObject forecastJson=new JSONObject(forecastJsonStr);
                JSONArray weatherArray=forecastJson.getJSONArray(OWM_LIST);

                Time dayTime=new Time();
                dayTime.setToNow();

                int julianStartDay=Time.getJulianDay(System.currentTimeMillis(),dayTime.gmtoff);

                dayTime=new Time();

                String[] resultStrs=new String[numDays];

                SharedPreferences sharedPref=PreferenceManager.getDefaultSharedPreferences(getActivity());
                String unitType=sharedPref.getString(getString(R.string.pref_units_key),getString(R.string.pref_units_metric));

                for (int i=0;i<weatherArray.length();i++)
                {
                    String day;
                    String description;
                    String highAndLow;

                    JSONObject dayForecast=weatherArray.getJSONObject(i);

                    long dateTime;

                    dateTime=dayTime.setJulianDay(julianStartDay+i);
                    day=getReadableDateString(dateTime);

                    JSONObject weatherObject=dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
                    description=weatherObject.getString(OWM_DESCRIPTION);

                    JSONObject temperatureObject=dayForecast.getJSONObject(OWM_TEMPERATURE);
                    double high=temperatureObject.getDouble(OWM_MAX);
                    double low=temperatureObject.getDouble(OWM_MIN);

                    highAndLow=formatHighLow(high,low,unitType);
                    resultStrs[i]=day + " - " + description + " - " + highAndLow;
                }

                for (String s:resultStrs)
                {
                    Log.v(LOG_TAG, "forecast entry" +s);
                }
                return resultStrs;
            }

            @Override
            protected String[] doInBackground(String...params)
            {
                if(params.length==0)
                {
                    return null;
                }
                HttpURLConnection urlConnection= null;
                BufferedReader reader=null;
                String forecastJsonStr=null;

                String format="json";
                String units="metric";
                int numDays=7;

                try
                {

                    final String FORECAST_BASE_URL="http://api.openweathermap.org/data/2.5/forecast/daily?";

                    final String QUERY_PARAM="q";
                    final String FORMAT_PARAM="mode";
                    final String UNIT_PARAM="days";
                    final String DAYS_PARAM="cnt";
                    final String APPID_PARAM="APPID";

                    Uri buildUri=Uri.parse(FORECAST_BASE_URL).buildUpon()
                            .appendQueryParameter(QUERY_PARAM, params[0])
                            .appendQueryParameter(FORMAT_PARAM,format)
                            .appendQueryParameter(UNIT_PARAM,units)
                            .appendQueryParameter(DAYS_PARAM,Integer.toString(numDays))
                            .appendQueryParameter(APPID_PARAM,BuildConfig.OPEN_WEATHER_MAP_API_KEY).build();

                    URL url=new URL(buildUri.toString());
                    Log.v(LOG_TAG,"build uri"+url);

                   // URL url= new URL("http://api.openweathermap.org/data/2.5/forecast/daily?q=94043&mode=json&units=metric&cnt=7");

                    urlConnection=(HttpURLConnection)url.openConnection();
                    urlConnection.setRequestMethod("GET");
                    urlConnection.connect();

                    InputStream inputStream=urlConnection.getInputStream();
                    StringBuffer buffer=new StringBuffer();

                    if (inputStream==null)
                    {
                        forecastJsonStr= null;
                    }

                    reader=new BufferedReader(new InputStreamReader(inputStream));
                    String line;

                    while ((line=reader.readLine())!=null)
                    {
                        buffer.append(line+"/n");
                    }

                    if (buffer.length()==0)
                    {
                        forecastJsonStr= null;
                    }
                    forecastJsonStr=buffer.toString();
                    Log.v(LOG_TAG,"forecast string:"+forecastJsonStr);
                }catch (IOException e)
                {
                    Log.e(LOG_TAG,"error",e);
                    forecastJsonStr= null;
                }finally
                {
                    if(urlConnection!=null)
                    {
                        urlConnection.disconnect();
                    }
                    if (reader!=null)
                    {
                        try {
                            reader.close();
                        }catch (final IOException e)
                        {
                            Log.e(LOG_TAG,"error closing stream",e);

                        }
                    }
                }

                try
                {
                    return getWeatherDataFromJson(forecastJsonStr,numDays);
                }
                catch (JSONException e)
                {
                    Log.e(LOG_TAG,e.getMessage(),e);
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void onPostExecute(String[] result)
            {
                if (result!=null)
                {
                    mForecastAdapter.clear();
                    for (String dayForecastString:result)
                    {
                        mForecastAdapter.add(dayForecastString);
                    }

                }

            }
        }

}