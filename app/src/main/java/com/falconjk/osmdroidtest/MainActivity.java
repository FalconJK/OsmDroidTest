package com.falconjk.osmdroidtest;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.MapTileIndex;
import org.osmdroid.views.MapView;
import org.osmdroid.api.IMapController;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.FolderOverlay;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity implements MapEventsReceiver {

    private MapView map;
    private Marker droneMarker; // 無人機標記
    private Polyline pathPolyline; // 航點路線

    private Handler handler;
    private Runnable moveDroneRunnable;
    private Button btn_switchLayer;
    private static final int MOVE_INTERVAL = 1000; // 每秒移動一次
    private List<ITileSource> tileSources;
    private LinkedHashMap<String, Marker> markersDict;

    private FolderOverlay markersFolder;
    private Button btn_center;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // 初始化OSMdroid配置
        Configuration.getInstance().load(getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));
        map = (MapView) findViewById(R.id.mapview);
        // 基本地圖設置
//        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getOverlays().add(0, new MapEventsOverlay(this)); // 添加到第一層，這樣不會被其他覆蓋層擋住

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

        btn_switchLayer = findViewById(R.id.btn_switch_layer);
        btn_switchLayer.setOnClickListener(v -> toggleMapLayer());

        btn_center = (Button) findViewById(R.id.btn_center);
        btn_center.setOnClickListener(v -> centerOnRoute());


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
        btn_switchLayer.setText("切換到" + sources.get(tileSourcesIndex).name() + "地圖");
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
        markersDict = new LinkedHashMap<>();
        pathPolyline = new Polyline();
        pathPolyline.getOutlinePaint().setColor(0xFF0000FF); // 藍色路線
        pathPolyline.getOutlinePaint().setStrokeWidth(5f);
        markersFolder = new FolderOverlay();
        map.getOverlays().add(markersFolder);
        map.getOverlays().add(pathPolyline);

        addWaypoint(new GeoPoint(25.0350, 121.5674)); // 航點1
        addWaypoint(new GeoPoint(25.0340, 121.5664)); // 航點2
        addWaypoint(new GeoPoint(25.0330, 121.5654)); // 航點3
    }

    // 更新無人機位置的方法
    public void updateDronePosition(double latitude, double longitude, float heading) {
        droneMarker.setPosition(new GeoPoint(latitude, longitude));
        droneMarker.setRotation(heading); // 單位為度
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

    private void initOnlineMap() {

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
                } catch (Exception e) {
                }
                return naeurl;
            }
        };

        tileSources = new ArrayList<>();
        tileSources.add(TileSourceFactory.MAPNIK);
        tileSources.add(wmst_emap_3857);
        tileSources.add(wmst_PHOTO_MIX_3857);
    }

    @Override
    public boolean singleTapConfirmedHelper(GeoPoint p) {
        runOnUiThread(() -> {
            Toast.makeText(this,
                    "點擊位置: " + p.getLatitude() + ", " + p.getLongitude(),
                    Toast.LENGTH_SHORT).show();
            addWaypoint(p);
        });
        return true;
    }

    private void addWaypoint(GeoPoint newPoint) {
        String uuid = UUID.randomUUID().toString();
        Marker newMarker = new Marker(map);
        newMarker.setPosition(newPoint);
        newMarker.setIcon(ContextCompat.getDrawable(this, R.drawable.location_on));
        newMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        newMarker.setTitle("航點" + (markersDict.size() + 1));
        newMarker.setRelatedObject(uuid);  // 保存 UUID
        newMarker.setOnMarkerClickListener((marker, mapView) -> {
            // 顯示對話框
            new AlertDialog.Builder(this)
                    .setTitle("航點操作")
                    .setItems(new String[]{"顯示資訊", "刪除航點"}, (dialog, which) -> {
                        switch (which) {
                            case 0: // 顯示資訊
                                marker.showInfoWindow();
                                break;
                            case 1: // 刪除航點
                                notifyDeleteMarker(marker);
                                break;
                        }
                    })
                    .show();
            return true; // 返回 true 表示我們已經處理了這個點擊事件
        });
        newMarker.setDraggable(true);
        newMarker.setOnMarkerDragListener(new Marker.OnMarkerDragListener() {
            @Override
            public void onMarkerDrag(Marker marker) {
                // 拖動過程中更新路徑
                updatePathForMarker(marker);
            }

            @Override
            public void onMarkerDragEnd(Marker marker) {
                // 拖動結束後更新路徑和航點列表
                updatePathForMarker(marker);
                Toast.makeText(MainActivity.this, "航點位置已更新", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onMarkerDragStart(Marker marker) {
                // 開始拖動時可以添加一些視覺效果或提示
                marker.closeInfoWindow(); // 如果有信息窗口打開，先關閉它
            }
        });

        markersFolder.add(newMarker);
        markersDict.put(uuid, newMarker);
        pathPolyline.addPoint(newPoint);
        map.invalidate();
    }

    private void updatePathForMarker(Marker marker) {
        String uuid = (String) marker.getRelatedObject();
        GeoPoint currPoint = marker.getPosition();

        if (uuid == null || !markersDict.containsKey(uuid)) return;
        markersDict.put(uuid, marker);
        pathPolyline.setPoints(getGeoPointList());
    }

    private void notifyDeleteMarker(Marker marker) {
        new AlertDialog.Builder(this)
                .setTitle("刪除航點")
                .setMessage("確定要刪除這個航點嗎？")
                .setPositiveButton("確定", (d, w) -> deleteWaypoint(marker))
                .setNegativeButton("取消", null)
                .show();
    }


    private void deleteWaypoint(Marker markerToDelete) {
        String uuid = (String) markerToDelete.getRelatedObject();
        if (uuid == null || !markersDict.containsKey(uuid)) return;

        markersDict.remove(uuid);
        markersFolder.remove(markerToDelete);
        pathPolyline.setPoints(getGeoPointList());
        updateWaypointTitles();
        map.invalidate();
        Toast.makeText(this, "已刪除航點", Toast.LENGTH_SHORT).show();
    }

    private void deleteWaypoint(GeoPoint p) {
        if (markersDict.isEmpty()) {
            return;
        }

        // 找到最近的航點
        double minDistance = Double.MAX_VALUE;
        Marker markerToDelete = null;

        for (Marker marker : markersDict.values()) {
            double distance = marker.getPosition().distanceToAsDouble(p);
            if (distance < minDistance) {
                minDistance = distance;
                markerToDelete = marker;
            }
        }

        // 如果找到的點距離點擊位置小於一定閾值（例如50米），則刪除該點
        if (markerToDelete != null && minDistance < 50) {
            notifyDeleteMarker(markerToDelete);
        }
    }


    // 修改 longPressHelper 方法來觸發刪除功能
    @Override
    public boolean longPressHelper(GeoPoint p) {
        runOnUiThread(() -> {
            deleteWaypoint(p);
        });
        return true;
    }

    // 更新所有航點的標題編號
    private void updateWaypointTitles() {
        AtomicInteger index = new AtomicInteger(1);
        markersDict.values().forEach(marker -> marker.setTitle("航點" + (index.getAndIncrement())));
    }

    private void centerOnRoute() {
        if (markersDict.isEmpty()) {
            Toast.makeText(this, "尚未設置航點", Toast.LENGTH_SHORT).show();
            return;
        }

        List<GeoPoint> points = getGeoPointList();

        // 如果只有一個航點
        if (points.size() == 1) {
            map.getController().animateTo(points.get(0));
            map.getController().setZoom(15.0);
            return;
        }

        // 創建一個邊界框來包含所有航點
        BoundingBox boundingBox = BoundingBox.fromGeoPoints(points);

        // 計算緯度和經度的跨度
        double latSpan = boundingBox.getLatNorth() - boundingBox.getLatSouth();
        double lonSpan = boundingBox.getLonEast() - boundingBox.getLonWest();

        // 添加 10% 的邊距
        BoundingBox boxWithMargin = new BoundingBox(
                boundingBox.getLatNorth() + (latSpan * 0.1),  // 北緯 + 邊距
                boundingBox.getLonEast() + (lonSpan * 0.1),   // 東經 + 邊距
                boundingBox.getLatSouth() - (latSpan * 0.1),  // 南緯 - 邊距
                boundingBox.getLonWest() - (lonSpan * 0.1)    // 西經 - 邊距
        );

        // 設置地圖邊界並添加動畫效果
        map.zoomToBoundingBox(boxWithMargin, true, 1);  // 1000ms = 1秒動畫
    }

    @NonNull
    private List<GeoPoint> getGeoPointList() {
        return markersDict.values().stream()
                .map(Marker::getPosition)
                .collect(Collectors.toList());
    }
}