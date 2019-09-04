package com.mapbox.mapboxandroiddemo.examples.extrusions;

import android.graphics.PointF;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.mapbox.api.tilequery.MapboxTilequery;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxandroiddemo.R;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.style.expressions.Expression;
import com.mapbox.mapboxsdk.style.layers.FillExtrusionLayer;
import com.mapbox.mapboxsdk.style.layers.PropertyValue;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;
import com.mapbox.mapboxsdk.style.sources.VectorSource;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;

import static android.graphics.Color.parseColor;
import static com.mapbox.mapboxsdk.style.expressions.Expression.all;
import static com.mapbox.mapboxsdk.style.expressions.Expression.eq;
import static com.mapbox.mapboxsdk.style.expressions.Expression.exponential;
import static com.mapbox.mapboxsdk.style.expressions.Expression.get;
import static com.mapbox.mapboxsdk.style.expressions.Expression.interpolate;
import static com.mapbox.mapboxsdk.style.expressions.Expression.linear;
import static com.mapbox.mapboxsdk.style.expressions.Expression.literal;
import static com.mapbox.mapboxsdk.style.expressions.Expression.stop;
import static com.mapbox.mapboxsdk.style.expressions.Expression.zoom;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.fillExtrusionBase;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.fillExtrusionColor;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.fillExtrusionHeight;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.fillExtrusionOpacity;

/**
 * Change the color of a selected building. Your Mapbox account must
 */
public class SingleHighlightedBuildingExtrusionActivity extends AppCompatActivity implements MapboxMap.OnMapClickListener {

  private static final String TAG = "SingleBuildingActivity";
  private static final String EXTRUSION_BUILDING_LAYER_LAYER_ID = "EXTRUSION_BUILDING_LAYER_LAYER_ID";
  private static final String HIGHLIGHTED_EXTRUSION_BUILDING_LAYER_ID = "HIGHLIGHTED_EXTRUSION_BUILDING_LAYER";
  private static final String EXTRUSION_BUILDING_VECTOR_SOURCE = "EXTRUSION_BUILDING_VECTOR_SOURCE"; // Do not change this
  private static final String HIGHLIGHTED_EXTRUSION_BUILDING_GEOJSON_SOURCE_ID = "HIGHLIGHTED_EXTRUSION_BUILDING_GEOJSON_SOURCE_ID"; // Do not change this
  private static final String COMPOSITE_SOURCE_ID = "COMPOSITE_SOURCE_ID"; // Do not change this
  private static final String HIGHLIGHTED_SOURCE_ID = "HIGHLIGHTED_SOURCE_ID"; // Do not change this
  private static final String PROPERTY_ID = "id"; // Do not change this
  private static final String PROPERTY_SELECTED = "PROPERTY_SELECTED";

  // Adjust the static final variables below to change the example's UI
  private static final float MIN_ZOOM = 15f;
  private static final String REGULAR_EXTRUSION_COLOR = "#1F6492";
  private static final String HIGHLIGHTED_EXTRUSION_COLOR = "#fb14ff";
  private static final float EXTRUSION_OPACITY = 0.8f;
  private static final Expression GET_HEIGHT_EXPRESSION = get("height");
  private static final Expression GET_MIN_HEIGHT_EXPRESSION = get("min_height");
  private static final PropertyValue<Expression> FILL_HEIGHT_PROPERTY_VALUE = fillExtrusionHeight(
    interpolate(
      exponential(1f),
      zoom(),
      stop(15, literal(0)),
      stop(15.05, GET_HEIGHT_EXPRESSION)
    ));

  private static final PropertyValue<Expression> FILL_BASE_PROPERTY_VALUE = fillExtrusionBase(
    interpolate(
      linear(),
      zoom(),
      stop(15, literal(0)),
      stop(15.05, GET_MIN_HEIGHT_EXPRESSION)
    ));

  private MapView mapView;
  private MapboxMap mapboxMap;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Mapbox access token is configured here. This needs to be called either in your application
    // object or in the same activity which contains the mapview.
    Mapbox.getInstance(this, getString(R.string.access_token));

    setContentView(R.layout.activity_extrusion_highlighted_building);

    mapView = findViewById(R.id.mapView);
    mapView.onCreate(savedInstanceState);
    mapView.getMapAsync(new OnMapReadyCallback() {
      @Override
      public void onMapReady(@NonNull final MapboxMap mapboxMap) {
        SingleHighlightedBuildingExtrusionActivity.this.mapboxMap = mapboxMap;
        mapboxMap.setStyle(new Style.Builder().fromUri(Style.LIGHT)
            .withSource(new VectorSource(EXTRUSION_BUILDING_VECTOR_SOURCE, "mapbox://mapbox.buildings-plus-v1"))
            .withSource(new GeoJsonSource(HIGHLIGHTED_EXTRUSION_BUILDING_GEOJSON_SOURCE_ID)),
          new Style.OnStyleLoaded() {
            @Override
            public void onStyleLoaded(@NonNull Style style) {
              initRegularBuildingExtrusionLayer(style);
              initHighlightedBuildingExtrusionLayer(style);
              mapboxMap.addOnMapClickListener(SingleHighlightedBuildingExtrusionActivity.this);
            }
          });
      }
    });
  }

  @Override
  public boolean onMapClick(@NonNull LatLng point) {
    handleClickIcon(point);
    return true;
  }

  /**
   * Use the Java SDK's MapboxTilequery class to build a API request and use the API response
   *
   * @param point the center point that the the tilequery will originate from.
   */
  private void makeTilequeryApiCall(@NonNull LatLng point) {
    MapboxTilequery tilequery = MapboxTilequery.builder()
      .accessToken(getString(R.string.access_token))
      .tilesetIds("mapbox.buildings-plus-v1")
      .query(Point.fromLngLat(point.getLongitude(), point.getLatitude()))
      .radius(10)
      .limit(5)
      .geometry("polygon")
      .dedupe(true)
      .layers("buildings_plus")
      .build();

    tilequery.enqueueCall(new Callback<FeatureCollection>() {
      @Override
      public void onResponse(Call<FeatureCollection> call, Response<FeatureCollection> response) {
        if (response.body() != null) {
          FeatureCollection responseFeatureCollection = response.body();
          if (responseFeatureCollection.features() != null) {
            for (Feature singleFeature : responseFeatureCollection.features()) {
              Log.d(TAG, "tilequery onResponse: singleFeature id = " + singleFeature.id());
            }
          }
        }
      }

      @Override
      public void onFailure(Call<FeatureCollection> call, Throwable throwable) {
        Timber.d("Request failed: %s", throwable.getMessage());
        Toast.makeText(SingleHighlightedBuildingExtrusionActivity.this, R.string.api_error, Toast.LENGTH_SHORT).show();
      }
    });
  }

  /*private Feature findParent(List<Feature> featureList) {
    *//*Feature clickedFeature = featureList.get(0);
    if (clickedFeature.properties().has("building:part")) {

      List<Feature> allFeatures = mapboxMap.queryRenderedFeatures(screenPoint, eq(clickedFeature.id(), ), EXTRUSION_BUILDING_LAYER_LAYER_ID);

      let parent;
      for (Feature feature : allFeatures) {
        if (feature.id() == clickedFeature.properties()) {
          parent = feature;
          return false;
        } else {
          return true;

        }
        return parent ? parent : clickedFeature;
      } else{
        return clickedFeature;
      }
    }*//*
  }*/

  /**
   * This method handles click events for SymbolLayer symbols.
   * <p>
   * When a SymbolLayer icon is clicked, we moved that feature to the selected state.
   * </p>
   *
   * @param clickLatLng the point on screen clicked
   */
  private void handleClickIcon(LatLng clickLatLng) {

    makeTilequeryApiCall(clickLatLng);

    PointF pointF = mapboxMap.getProjection().toScreenLocation(clickLatLng);

    mapboxMap.getStyle(new Style.OnStyleLoaded() {
      @Override
      public void onStyleLoaded(@NonNull Style style) {
        List<Feature> listOfTappedOnQueryBuilding = mapboxMap.queryRenderedFeatures(pointF,
            EXTRUSION_BUILDING_LAYER_LAYER_ID);



        if (!listOfTappedOnQueryBuilding.isEmpty()) {
          Feature selectedUnhighlightedBuildingFeature = listOfTappedOnQueryBuilding.get(0);
//          boolean whetherSelectedBuildingIsHighlighted = selectedUnhighlightedBuildingFeature.getBooleanProperty(PROPERTY_SELECTED);

          setFeatureSelectState(selectedUnhighlightedBuildingFeature, true);


          /*if () {
            setFeatureSelectState(selectedUnhighlightedBuildingFeature, false);
          } else {
            setSelected(selectedUnhighlightedBuildingFeature);
          }*/

          /*if (featureList != null) {
            for (int i = 0; i < featureList.size(); i++) {
              if (featureList.get(i).getStringProperty(PROPERTY_SELECTED).equals(selectedUnhighlightedBuildingFeature)) {

              }
            }
          }*/
        }


















        if (!listOfTappedOnQueryBuilding.isEmpty()) {
          Feature selectedUnhighlightedBuildingFeature = listOfTappedOnQueryBuilding.get(0);
          Log.d(TAG, "handleClickIcon: selectedUnhighlightedBuildingFeature id = " + selectedUnhighlightedBuildingFeature.id());


//          findParent(listOfTappedOnQueryBuilding);
/*

          let ids = [parent.id];
          if (parent.properties.parts) {
            ids = ids.concat(JSON.parse(parent.properties.parts));
          }

          selectFeatures(ids);

*/


          /*if (featureSelectStatus(renderedFeaturesFromQuery.get(0))) {
            setFeatureSelectState(renderedFeaturesFromQuery.get(0), false);
          } else {
          }*/


          /*setFeatureSelectState(listOfTappedOnQueryBuilding.get(0), true);

          GeoJsonSource extrusionCompositeSourceId = style.getSourceAs(HIGHLIGHTED_EXTRUSION_BUILDING_GEOJSON_SOURCE_ID);
          if (extrusionCompositeSourceId != null) {
            extrusionCompositeSourceId.setGeoJson(listOfTappedOnQueryBuilding.get(0));
          }*/
        } else {
          Log.d(TAG, "listOfTappedOnQueryBuilding.isEmpty()");
        }
      }
    });
  }

  /**
   * Set a feature selected state.
   *
   * @param selectedFeature the selected feature
   */
  private void setSelected(Feature selectedFeature) {
    setFeatureSelectState(selectedFeature, true);
    refreshSource(selectedFeature);
  }

  /**
   * Selects the state of a feature
   *
   * @param feature the feature to be selected.
   */
  private void setFeatureSelectState(Feature feature, boolean selectedState) {
    feature.properties().addProperty(PROPERTY_SELECTED, selectedState);
    refreshSource(feature);
  }

  /**
   * Checks whether a Feature's boolean "selected" property is true or false
   *
   * @param singleFeatureToAdjust the specific Feature to check
   * @return true if "selected" is true. False if the boolean property is false.
   */
  private boolean featureSelectStatus(Feature singleFeatureToAdjust) {
    return singleFeatureToAdjust.getBooleanProperty(PROPERTY_SELECTED);
  }

  /**
   * Updates the display of data on the map after the FeatureCollection has been modified
   */
  private void refreshSource(Feature feature) {
    mapboxMap.getStyle(new Style.OnStyleLoaded() {
      @Override
      public void onStyleLoaded(@NonNull Style style) {
        GeoJsonSource highlightGeoJsonSource = style.getSourceAs(HIGHLIGHTED_EXTRUSION_BUILDING_GEOJSON_SOURCE_ID);
        if (highlightGeoJsonSource != null) {
          highlightGeoJsonSource.setGeoJson(feature);
        }
      }
    });
  }

  private void initRegularBuildingExtrusionLayer(@NonNull Style loadedMapStyle) {
    String highestLabelLayerId;
    /*for (Layer singlelayer : loadedMapStyle.getLayers()) {

      if (singlelayer instanceof SymbolLayer && singlelayer.layout["text-field"]) {
        highestLabelLayerId = singlelayer.getId();
        break;
      }
    }*/
    FillExtrusionLayer fillExtrusionLayer = new FillExtrusionLayer(EXTRUSION_BUILDING_LAYER_LAYER_ID, EXTRUSION_BUILDING_VECTOR_SOURCE);
    fillExtrusionLayer.setSourceLayer("buildings_plus");
    fillExtrusionLayer.setFilter(eq(get("extrude"), "true"));
    fillExtrusionLayer.setMinZoom(MIN_ZOOM);
    fillExtrusionLayer.setProperties(
      fillExtrusionColor(parseColor(REGULAR_EXTRUSION_COLOR)),
      FILL_HEIGHT_PROPERTY_VALUE,
      FILL_BASE_PROPERTY_VALUE,
      fillExtrusionOpacity(EXTRUSION_OPACITY));
    if (loadedMapStyle.getLayer("road-label") != null) {
      loadedMapStyle.addLayerBelow(fillExtrusionLayer, "road-label");
    } else {
      loadedMapStyle.addLayer(fillExtrusionLayer);
    }
  }

  private void initHighlightedBuildingExtrusionLayer(@NonNull Style loadedMapStyle) {
    if (loadedMapStyle.getLayer(HIGHLIGHTED_EXTRUSION_BUILDING_LAYER_ID) != null) {
      loadedMapStyle.removeLayer(HIGHLIGHTED_EXTRUSION_BUILDING_LAYER_ID);
    }
    FillExtrusionLayer highlightedFillExtrusionLayer = new FillExtrusionLayer(HIGHLIGHTED_EXTRUSION_BUILDING_LAYER_ID, HIGHLIGHTED_EXTRUSION_BUILDING_GEOJSON_SOURCE_ID);
    highlightedFillExtrusionLayer.setSourceLayer("buildings_plus");
    highlightedFillExtrusionLayer.withFilter(all(eq(get("extrude"), "true"), eq((get(PROPERTY_SELECTED)), literal(true))));
    highlightedFillExtrusionLayer.setMinZoom(MIN_ZOOM);
/*    highlightedFillExtrusionLayer.setProperties(
      fillExtrusionColor(Color.parseColor(HIGHLIGHTED_EXTRUSION_COLOR)),
      fillExtrusionBase(get("min_height")),
      FILL_HEIGHT_PROPERTY_VALUE,
      fillExtrusionOpacity(EXTRUSION_OPACITY));*/
    highlightedFillExtrusionLayer.setProperties(
        fillExtrusionColor(parseColor(HIGHLIGHTED_EXTRUSION_COLOR)),
        FILL_HEIGHT_PROPERTY_VALUE,
        FILL_BASE_PROPERTY_VALUE,
        fillExtrusionOpacity(EXTRUSION_OPACITY));
    loadedMapStyle.addLayerAbove(highlightedFillExtrusionLayer, EXTRUSION_BUILDING_LAYER_LAYER_ID);
  }

  @Override
  protected void onStart() {
    super.onStart();
    mapView.onStart();
  }

  @Override
  protected void onResume() {
    super.onResume();
    mapView.onResume();
  }

  @Override
  protected void onPause() {
    super.onPause();
    mapView.onPause();
  }

  @Override
  protected void onStop() {
    super.onStop();
    mapView.onStop();
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    mapView.onSaveInstanceState(outState);
  }

  @Override
  public void onLowMemory() {
    super.onLowMemory();
    mapView.onLowMemory();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (mapboxMap != null) {
      mapboxMap.removeOnMapClickListener(this);
    }
    mapView.onDestroy();
  }
}

