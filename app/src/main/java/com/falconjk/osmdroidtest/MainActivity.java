package com.falconjk.osmdroidtest;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.MapTileIndex;
import org.osmdroid.views.MapView;
import org.osmdroid.api.IMapController;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;


import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private MapView map;
    private Marker droneMarker; // 無人機標記
    private Polyline pathPolyline; // 航點路線

    private Handler handler;
    private Runnable moveDroneRunnable;
    private Button switchLayerButton;
    private static final int MOVE_INTERVAL = 1000; // 每秒移動一次
    private List<ITileSource> tileSources;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // 初始化OSMdroid配置
        Configuration.getInstance().load(getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));
        map = (MapView) findViewById(R.id.mapview);
        // 基本地圖設置
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);

        // 初始化地圖控制器
        IMapController mapController = map.getController();
        mapController.setZoom(15.0);

        // 設置初始位置（例如：台北市中心）
        GeoPoint startPoint = new GeoPoint(25.0330, 121.5654);
        mapController.setCenter(startPoint);

        // 初始化無人機標記
        initDroneMarker();

        // 初始化航點路線
        initWaypoints();

        initRandomMovement();

        switchLayerButton = findViewById(R.id.switch_layer_button);
        switchLayerButton.setOnClickListener(v -> toggleMapLayer());
        initOnlineMap();
        toggleMapLayer();
    }

    private int tileSourcesIndex = 0;


    private void toggleMapLayer() {
//        List<ITileSource> sources = TileSourceFactory.getTileSources();
        List<ITileSource> sources = tileSources;
        ITileSource iTileSource = sources.get(tileSourcesIndex);
        tileSourcesIndex = (tileSourcesIndex + 1) % sources.size();
        map.setTileSource(iTileSource);
        switchLayerButton.setText("切換到" + sources.get(tileSourcesIndex).name()+"地圖");
        map.invalidate();
    }

    private void initDroneMarker() {
        droneMarker = new Marker(map);
        droneMarker.setIcon(ContextCompat.getDrawable(this, R.drawable.baseline_navigation_24));
        droneMarker.setTitle("無人機位置");
        map.getOverlays().add(droneMarker);
        updateDronePosition(25.0360, 121.5674, 0);
    }

    private void initWaypoints() {
        // 創建航點列表（示例航點）
        List<GeoPoint> waypoints = new ArrayList<>();
        waypoints.add(new GeoPoint(25.0330, 121.5654)); // 航點1
        waypoints.add(new GeoPoint(25.0340, 121.5664)); // 航點2
        waypoints.add(new GeoPoint(25.0350, 121.5674)); // 航點3

        // 在地圖上標記航點
        for (GeoPoint point : waypoints) {
            Marker waypoint = new Marker(map);
            waypoint.setPosition(point);
            waypoint.setIcon(ContextCompat.getDrawable(this, android.R.drawable.ic_menu_myplaces));
            waypoint.setTitle("航點");
            map.getOverlays().add(waypoint);
        }

        // 繪製航點之間的連線
        pathPolyline = new Polyline();
        pathPolyline.setPoints(waypoints);
        pathPolyline.getOutlinePaint().setColor(0xFF0000FF); // 藍色路線
        pathPolyline.getOutlinePaint().setStrokeWidth(5f);
        map.getOverlays().add(pathPolyline);
    }

    // 更新無人機位置的方法
    public void updateDronePosition(double latitude, double longitude, float heading) {
        GeoPoint newPosition = new GeoPoint(latitude, longitude);
        droneMarker.setPosition(newPosition);

        // 設置無人機標記的方位角
        droneMarker.setRotation(heading); // 單位為度

        // 更新標記的圖標，以反映方位角
        // 這裡可以使用一個指向箭頭的圖標
        droneMarker.setIcon(ContextCompat.getDrawable(this, R.drawable.baseline_navigation_24)); // 需要您提供指向箭頭的圖標

        map.invalidate(); // 重繪地圖
    }

    private void initRandomMovement() {
        handler = new Handler(Looper.getMainLooper());
        moveDroneRunnable = new Runnable() {
            @Override
            public void run() {
                moveDroneRandomly();
                handler.postDelayed(this, MOVE_INTERVAL);
            }
        };
    }

    private void moveDroneRandomly() {
        // 隨機生成新的位置和方位角
        double latitudeDelta = (Math.random() - 0.5) * 0.001; // 隨機移動範圍為經緯度0.001度
        double longitudeDelta = (Math.random() - 0.5) * 0.001;
        float headingDelta = (float) (Math.random() * 10);
        double newLatitude = droneMarker.getPosition().getLatitude() + latitudeDelta;
        double newLongitude = droneMarker.getPosition().getLongitude() + longitudeDelta;
        float newHeading = droneMarker.getRotation() + headingDelta;

        // 更新無人機位置和方位角
        updateDronePosition(newLatitude, newLongitude, newHeading);
    }


    @Override
    protected void onResume() {
        super.onResume();
        map.onResume();
        handler.post(moveDroneRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        map.onPause();
        handler.removeCallbacks(moveDroneRunnable);
    }

    private void initOnlineMap(){

        OnlineTileSourceBase wmst_emap_3857 = new XYTileSource("wmst_emap_3857", 5, 20, 256, ".png", new String[]{"https://wmts.nlsc.gov.tw/wmts/EMAP/default/EPSG:3857/"}) {
            @Override
            public String getTileURLString(final long pMapTileIndex) {
                String naeurl = "";
                try {
                    naeurl = getBaseUrl() + MapTileIndex.getZoom(pMapTileIndex) + "/" + MapTileIndex.getY(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex);
                    //Log.e( "zoom",MapTileIndex.getZoom( pMapTileIndex )+"");
                } catch (Exception e) {
                    //Log.e( "initimaperror", e.getMessage() );
                }
                return naeurl;
            }
        };

        OnlineTileSourceBase wmst_PHOTO_MIX_3857 = new XYTileSource("wmst_PHOTO_MIX_3857", 6, 20, 256, ".png", new String[]{"https://wmts.nlsc.gov.tw/wmts/PHOTO_MIX/default/EPSG:3857/"}) {
            @Override
            public String getTileURLString(final long pMapTileIndex) {
                String naeurl = "";
                try {
                    naeurl = getBaseUrl() + MapTileIndex.getZoom(pMapTileIndex) + "/" + MapTileIndex.getY(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex);
                    //Log.e( "zoom",MapTileIndex.getZoom( pMapTileIndex )+"");
                } catch (Exception e) {
                    //Log.e( "initimaperror", e.getMessage() );
                }
                return naeurl;
            }
        };

        tileSources = new ArrayList<>();
        tileSources.add(TileSourceFactory.MAPNIK);
        tileSources.add(wmst_emap_3857);
        tileSources.add(wmst_PHOTO_MIX_3857);
    }
}