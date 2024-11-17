package com.falconjk.osmdroidtest;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.view.View;
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

    private static final int ARROWS_PER_LINE = 2; // 每條線上的箭頭數量，可以根據需求調整

    private MapView map;
    private Marker droneMarker; // 無人機標記
    private Polyline pathInnerPolyline; // 航點路線

    private Handler handler;
    private Runnable moveDroneRunnable;
    private Button btn_switchLayer;
    private static final int MOVE_INTERVAL = 1000; // 每秒移動一次
    private List<ITileSource> tileSources;
    private LinkedHashMap<String, Marker> markersDict;
    private LinkedHashMap<String, Marker> arrowsDict;

    private FolderOverlay markersFolder;
    private Button btn_center;
    private Handler mHandler;
    private Polyline pathOutterPolyline;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // 初始化OSMdroid配置
        Configuration.getInstance().load(getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));
        mHandler = new Handler(Looper.getMainLooper());
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
        arrowsDict = new LinkedHashMap<>();
        pathInnerPolyline = new Polyline();
        pathOutterPolyline = new Polyline();
        pathInnerPolyline.getOutlinePaint().setColor(getColor(R.color.route_inner_color)); // 藍色路線
        pathOutterPolyline.getOutlinePaint().setColor(getColor(R.color.color_bright_blue)); // 藍色路線
        pathInnerPolyline.getOutlinePaint().setStrokeWidth(13f);
        pathOutterPolyline.getOutlinePaint().setStrokeWidth(22f);
        markersFolder = new FolderOverlay();
        map.getOverlays().add(pathOutterPolyline);
        map.getOverlays().add(pathInnerPolyline);
        map.getOverlays().add(markersFolder);

        addWaypoint(new GeoPoint(25.0350, 121.5674), false); // 航點1
        addWaypoint(new GeoPoint(25.0340, 121.5664), false); // 航點2
        addWaypoint(new GeoPoint(25.0330, 121.5654), false); // 航點3
        map.invalidate();
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
            addWaypoint(p, true);
        });
        return true;
    }

    private void addWaypoint(GeoPoint newPoint, boolean invalidateNow) {
        String uuid = UUID.randomUUID().toString();
        Marker newMarker = new Marker(map);
        newMarker.setPosition(newPoint);
        // 創建帶數字的標記視圖
        NumberedMarkerView markerView = new NumberedMarkerView(this);
        markerView.setNumber(String.valueOf(markersDict.size() + 1));

        // 使用固定尺寸創建位圖
        Bitmap bitmap = Bitmap.createBitmap(
                NumberedMarkerView.MARKER_SIZE,
                NumberedMarkerView.MARKER_SIZE,
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // 強制進行一次布局
        markerView.measure(
                View.MeasureSpec.makeMeasureSpec(NumberedMarkerView.MARKER_SIZE, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(NumberedMarkerView.MARKER_SIZE, View.MeasureSpec.EXACTLY)
        );
        markerView.layout(0, 0, NumberedMarkerView.MARKER_SIZE, NumberedMarkerView.MARKER_SIZE);

        markerView.draw(canvas);
        BitmapDrawable drawable = new BitmapDrawable(getResources(), bitmap);

        newMarker.setIcon(drawable);
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

        if (!markersDict.isEmpty()) {
            Marker lastMarker = markersDict.values().stream().reduce((a, b) -> b).orElse(null);
            if (lastMarker != null) {
                updatePolylineArrowMarkers(lastMarker, newMarker);
            }
        }


        markersFolder.add(newMarker);
        markersDict.put(uuid, newMarker);
        pathInnerPolyline.addPoint(newPoint);
        pathOutterPolyline.addPoint(newPoint);
        if (invalidateNow)
            map.invalidate();
    }

    private void updatePolylineArrowMarkers(Marker startMarker, Marker endMarker) {
        GeoPoint startPoint = startMarker.getPosition();
        GeoPoint endPoint = endMarker.getPosition();
        String uuidNext = (String) endMarker.getRelatedObject();

        // 計算方位角
        double bearing = Math.toDegrees(Math.atan2(
                endPoint.getLongitude() - startPoint.getLongitude(),
                endPoint.getLatitude() - startPoint.getLatitude()
        ));
        float rotation = (float) (360 - bearing) % 360;

        // 計算每段的距離比例
        double segmentRatio = 1.0 / (ARROWS_PER_LINE + 1);

        // 更新或創建箭頭標記
        for (int i = 1; i <= ARROWS_PER_LINE; i++) {
            // 計算箭頭位置
            double ratio = segmentRatio * i;
            GeoPoint arrowPosition = new GeoPoint(
                    startPoint.getLatitude() + (endPoint.getLatitude() - startPoint.getLatitude()) * ratio,
                    startPoint.getLongitude() + (endPoint.getLongitude() - startPoint.getLongitude()) * ratio
            );

            String arrowId = uuidNext + "_" + i;
            Marker arrowMarker = arrowsDict.get(arrowId);

            // 如果箭頭不存在，創建新的
            if (arrowMarker == null) {
                arrowMarker = new Marker(map);
                arrowMarker.setIcon(ContextCompat.getDrawable(this, R.drawable.baseline_keyboard_arrow_up_24));
                arrowMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
                arrowMarker.setDraggable(false);
                arrowMarker.setRelatedObject(arrowId);
                arrowsDict.put(arrowId, arrowMarker);
                markersFolder.add(arrowMarker);
            }

            // 更新位置和旋轉
            arrowMarker.setPosition(arrowPosition);
            arrowMarker.setRotation(rotation);
        }
    }

    private void updatePathForMarker(Marker marker) {
        String uuid = (String) marker.getRelatedObject();
        if (uuid == null || !markersDict.containsKey(uuid)) return;

        markersDict.put(uuid, marker);
        ArrayList<Marker> markers = new ArrayList<>(markersDict.values());
        int index = markers.indexOf(marker);

        // 更新與前一個點之間的箭頭
        if (index > 0) {
            updatePolylineArrowMarkers(markers.get(index - 1), marker);
        }

        // 更新與後一個點之間的箭頭
        if (index < markers.size() - 1) {
            updatePolylineArrowMarkers(marker, markers.get(index + 1));
        }

        List<GeoPoint> waypointPointList = getWaypointPointList();
        pathInnerPolyline.setPoints(waypointPointList);
        pathOutterPolyline.setPoints(waypointPointList);
        map.invalidate();
    }

    // 更新所有標記的數字
    private void updateWaypointNumbers() {
        ArrayList<Marker> markers = new ArrayList<>(markersDict.values());
        for (int i = 0; i < markers.size(); i++) {
            Marker marker = markers.get(i);
            NumberedMarkerView markerView = new NumberedMarkerView(this);
            markerView.setNumber(String.valueOf(i + 1));

            Bitmap bitmap = Bitmap.createBitmap(
                    NumberedMarkerView.MARKER_SIZE,
                    NumberedMarkerView.MARKER_SIZE,
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);

            markerView.measure(
                    View.MeasureSpec.makeMeasureSpec(NumberedMarkerView.MARKER_SIZE, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(NumberedMarkerView.MARKER_SIZE, View.MeasureSpec.EXACTLY)
            );
            markerView.layout(0, 0, NumberedMarkerView.MARKER_SIZE, NumberedMarkerView.MARKER_SIZE);

            markerView.draw(canvas);
            BitmapDrawable drawable = new BitmapDrawable(getResources(), bitmap);

            marker.setIcon(drawable);
        }
        map.invalidate();
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

        ArrayList<Marker> markers = new ArrayList<>(markersDict.values());
        int index = markers.indexOf(markerToDelete);

        // 1. 刪除指向當前點的箭頭（來自前一個點的箭頭）
        if (index > 0) {
            String currentUuid = uuid;
            for (int i = 1; i <= ARROWS_PER_LINE; i++) {
                String arrowId = currentUuid + "_" + i;
                Marker arrowMarker = arrowsDict.remove(arrowId);
                if (arrowMarker != null) {
                    markersFolder.remove(arrowMarker);
                }
            }
        }

        // 2. 刪除從當前點指向下一個點的箭頭
        if (index < markers.size() - 1) {
            String nextUuid = (String) markers.get(index + 1).getRelatedObject();
            for (int i = 1; i <= ARROWS_PER_LINE; i++) {
                String arrowId = nextUuid + "_" + i;
                Marker arrowMarker = arrowsDict.remove(arrowId);
                if (arrowMarker != null) {
                    markersFolder.remove(arrowMarker);
                }
            }
        }

        // 3. 如果需要連接前後點，創建新的箭頭
        if (index > 0 && index < markers.size() - 1) {
            Marker prevMarker = markers.get(index - 1);
            Marker nextMarker = markers.get(index + 1);
            updatePolylineArrowMarkers(prevMarker, nextMarker);
        }

        // 4. 刪除waypoint本身
        markersDict.remove(uuid);
        markersFolder.remove(markerToDelete);

        // 5. 更新路徑和標題
        List<GeoPoint> waypointPointList = getWaypointPointList();
        pathOutterPolyline.setPoints(waypointPointList);
        pathInnerPolyline.setPoints(waypointPointList);
        updateWaypointTitles();
        updateWaypointNumbers();
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

        List<GeoPoint> points = getWaypointPointList();

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
    private List<GeoPoint> getWaypointPointList() {
        return markersDict.values().stream()
                .map(Marker::getPosition)
                .collect(Collectors.toList());
    }
}