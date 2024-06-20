/*
 * Copyright 2018 Google LLC. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.ar.sceneform.samples.gltf;

import static java.util.concurrent.TimeUnit.SECONDS;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.ArraySet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.filament.gltfio.Animator;
import com.google.android.filament.gltfio.FilamentAsset;
import com.google.ar.core.Anchor;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.HitTestResult;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.Material;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.BaseArFragment;
import com.google.ar.sceneform.ux.TransformableNode;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * This is an example activity that uses the Sceneform UX package to make common AR tasks easier.
 */
public class GltfActivity extends AppCompatActivity {
  private static final String TAG = GltfActivity.class.getSimpleName();
  private static final double MIN_OPENGL_VERSION = 3.0;

  private ArFragment arFragment;
  private Renderable renderable;
  private ArrayList<AnchorNode> previewAnchors = new ArrayList<>(); // Biến lưu trữ AnchorNode hiện tại

    private static class LineRenderable {
        AnchorNode startNode;
        AnchorNode endNode;
        AnchorNode lineNode;
        ModelRenderable modelRenderable;

        LineRenderable(AnchorNode startNode, AnchorNode endNode, AnchorNode lineNode, ModelRenderable modelRenderable) {
            this.startNode = startNode;
            this.endNode = endNode;
            this.lineNode = lineNode;
            this.modelRenderable = modelRenderable;
        }
    }

  private static class AnimationInstance {
    Animator animator;
    Long startTime;
    float duration;
    int index;

    AnimationInstance(Animator animator, int index, Long startTime) {
      this.animator = animator;
      this.startTime = startTime;
      this.duration = animator.getAnimationDuration(index);
      this.index = index;
    }
  }

  private final Set<AnimationInstance> animators = new ArraySet<>();

  private final List<Color> colors =
      Arrays.asList(
          new Color(0, 0, 0, 1),
          new Color(1, 0, 0, 1),
          new Color(0, 1, 0, 1),
          new Color(0, 0, 1, 1),
          new Color(1, 1, 0, 1),
          new Color(0, 1, 1, 1),
          new Color(1, 0, 1, 1),
          new Color(1, 1, 1, 1));
  private int nextColor = 0;
  private ArrayList<Anchor> anchors = new ArrayList<>();
  private ArrayList<LineRenderable> lines = new ArrayList<>(); // Lưu trữ thông tin về các đoạn thẳng

  @SuppressLint("ClickableViewAccessibility")
  @Override
  @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
  // CompletableFuture requires api level 24
  // FutureReturnValueIgnored is not valid
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    if (!checkIsSupportedDeviceOrFinish(this)) {
      return;
    }

    setContentView(R.layout.activity_ux);
    arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);

    WeakReference<GltfActivity> weakActivity = new WeakReference<>(this);

    ModelRenderable.builder()
        .setSource(
            this,
            R.raw.anchor)
        .setIsFilamentGltf(true)
        .build()
        .thenAccept(
            modelRenderable -> {
              GltfActivity activity = weakActivity.get();
              if (activity != null) {
                activity.renderable = modelRenderable;
              }
            })
        .exceptionally(
            throwable -> {
              Toast toast =
                  Toast.makeText(this, "Unable to load Tiger renderable", Toast.LENGTH_LONG);
              toast.setGravity(Gravity.CENTER, 0, 0);
              toast.show();
              return null;
            });
//
//    arFragment.setOnTapArPlaneListener(
//        (HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {
//
//        });
      arFragment.setOnTapArPlaneListener(new BaseArFragment.OnTapArPlaneListener() {
          @Override
          public void onTapPlane(HitResult hitResult, Plane plane, MotionEvent motionEvent) {
              if (renderable == null) {
                  return;
              }

              // Create the Anchor.
              Anchor anchor = hitResult.createAnchor();
              AnchorNode anchorNode = new AnchorNode(anchor);
              anchorNode.setParent(arFragment.getArSceneView().getScene());
              anchors.add(anchor);

              // Update selectedAnchorNode
              selectedAnchorNode = anchorNode;

              // Create the transformable model and add it to the anchor.
              TransformableNode model = new TransformableNode(arFragment.getTransformationSystem());
              model.setParent(anchorNode);
              model.setRenderable(renderable);

              model.setWorldScale(new Vector3(0.01f, 0.01f, 0.01f)); // Thiết lập kích thước ban đầu
              model.getScaleController().setMinScale(0.01f); // Thiết lập kích thước tối thiểu
              model.getScaleController().setMaxScale(0.02f); // Thiết lập kích thước tối đa

              arFragment.getArSceneView().getScene().addChild(anchorNode);

              model.select();

              FilamentAsset filamentAsset = model.getRenderableInstance().getFilamentAsset();
              if (filamentAsset.getAnimator().getAnimationCount() > 0) {
                  animators.add(new AnimationInstance(filamentAsset.getAnimator(), 0, System.nanoTime()));
              }

              Color color = colors.get(nextColor);
              nextColor++;
              for (int i = 0; i < renderable.getSubmeshCount() -1; ++i) {
                  Material material = renderable.getMaterial(i);
                  material.setFloat4("baseColorFactor", color);
              }

              Node tigerTitleNode = new Node();
              tigerTitleNode.setParent(model);
              tigerTitleNode.setEnabled(false);
              tigerTitleNode.setLocalPosition(new Vector3(0.0f, 1.0f, 0.0f));
              ViewRenderable.builder()
                      .setView(weakActivity.get(), R.layout.tiger_card_view)
                      .build()
                      .thenAccept(
                              (renderable) -> {
                                  tigerTitleNode.setRenderable(renderable);
                                  tigerTitleNode.setEnabled(true);
                              })
                      .exceptionally(
                              (throwable) -> {
                                  throw new AssertionError("Could not load card view.", throwable);
                              }
                      );

              for (int i = 0; i < anchors.size() - 1; i++) {
                  drawLineWithText(anchors.get(i), anchors.get(i + 1));
              }

              // If there are two anchors, draw a line between them
//              if (anchors.size() == 2) {
////                drawLine(anchors.get(0), anchors.get(1));
//                  drawLineWithText(anchors.get(0), anchors.get(1));
//              }
          }

          @Override
          public void onCreateAnchor(Anchor anchor, Trackable trackable) {
              AnchorNode anchorNode = new AnchorNode(anchor);
              anchorNode.setParent(arFragment.getArSceneView().getScene());
              anchors.add(anchor);
              // Create the transformable model and add it to the anchor.
              TransformableNode model = new TransformableNode(arFragment.getTransformationSystem());
              model.setParent(anchorNode);
              model.setRenderable(renderable);

              model.setWorldScale(new Vector3(0.01f, 0.01f, 0.01f)); // Thiết lập kích thước ban đầu
              model.getScaleController().setMinScale(0.01f); // Thiết lập kích thước tối thiểu
              model.getScaleController().setMaxScale(0.02f); // Thiết lập kích thước tối đa

              arFragment.getArSceneView().getScene().addChild(anchorNode);

              model.select();

              FilamentAsset filamentAsset = model.getRenderableInstance().getFilamentAsset();
              if (filamentAsset.getAnimator().getAnimationCount() > 0) {
                  animators.add(new AnimationInstance(filamentAsset.getAnimator(), 0, System.nanoTime()));
              }

              Color color = colors.get(nextColor);
              nextColor++;
              for (int i = 0; i < renderable.getSubmeshCount(); ++i) {
                  Material material = renderable.getMaterial(i);
                  material.setFloat4("baseColorFactor", color);
              }

              Node tigerTitleNode = new Node();
              tigerTitleNode.setParent(model);
              tigerTitleNode.setEnabled(false);
              tigerTitleNode.setLocalPosition(new Vector3(0.0f, 1.0f, 0.0f));
              ViewRenderable.builder()
                      .setView(weakActivity.get(), R.layout.tiger_card_view)
                      .build()
                      .thenAccept(
                              (renderable) -> {
                                  tigerTitleNode.setRenderable(renderable);
                                  tigerTitleNode.setEnabled(true);
                              })
                      .exceptionally(
                              (throwable) -> {
                                  throw new AssertionError("Could not load card view.", throwable);
                              }
                      );

              // If there are two anchors, draw a line between them
//              if (anchors.size() == 2) {
////                drawLine(anchors.get(0), anchors.get(1));
//                  drawLineWithText(anchors.get(0), anchors.get(1));
//              }
              for (int i = 0; i < anchors.size() - 1; i++) {
                  drawLineWithText(anchors.get(i), anchors.get(i + 1));
              }
          }
      });

      arFragment.getArSceneView().getScene().setOnTouchListener(new Scene.OnTouchListener() {
          @Override
          public boolean onSceneTouch(HitTestResult hitTestResult, MotionEvent motionEvent) {
              if(motionEvent.getAction() == MotionEvent.ACTION_DOWN){
                  return false;
              }

              if(motionEvent.getAction() == MotionEvent.ACTION_UP){
                  return false;
              }

              if(motionEvent.getAction() == MotionEvent.ACTION_MOVE){
                  MotionEvent tap = MotionEvent.obtain(motionEvent.getDownTime(), motionEvent.getEventTime(), MotionEvent.ACTION_DOWN, motionEvent.getX(), motionEvent.getY(), 0);
                  Frame frame = arFragment.getArSceneView().getArFrame();
                  if (frame != null) {
                      List<HitResult> hitResults = frame.hitTest(tap);
                      if (!hitResults.isEmpty()) {
                          HitResult hitResult = hitResults.get(0);
                          Anchor newAnchor = hitResult.createAnchor();
                          if (selectedAnchorNode != null) {
                              selectedAnchorNode.setAnchor(newAnchor);
                          }
                      }
                  }
              }

              return false;
          }
      });

    arFragment
        .getArSceneView()
        .getScene()
        .addOnUpdateListener(
            frameTime -> {
                if(arFragment.getArSceneView().getSession()!=null){
                    Config config = arFragment.getArSceneView().getSession().getConfig();
                    config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL);
                    arFragment.getArSceneView().getSession().configure(config);
                }

              Long time = System.nanoTime();
              for (AnimationInstance animator : animators) {
                animator.animator.applyAnimation(
                    animator.index,
                    (float) ((time - animator.startTime) / (double) SECONDS.toNanos(1))
                        % animator.duration);
                animator.animator.updateBoneMatrices();
              }

                // Cập nhật vị trí của các đoạn thẳng
                for (LineRenderable line : lines) {
                    updateLine(line);
                }

              if(anchors.isEmpty()){
                  return;
              }

//                // Xoá toàn bộ AnchorNode cũ nếu có
//                removeAllAnchorNodes();
//
//                // Lấy mặt phẳng hiện tại được detect bởi ARCore
//                Collection<Plane> planes = arFragment.getArSceneView().getSession().getAllTrackables(Plane.class);
//                Pose cameraPose = arFragment.getArSceneView().getScene().getCamera().getPose();
//
//                // Hiển thị anchor preview tại vị trí và hướng của mặt phẳng đầu tiên được detect (nếu có)
//                for (Plane plane : planes) {
//                    if (plane.getTrackingState() == TrackingState.TRACKING) {
//
//                        Pose anchorPose = Pose.makeTranslation(cameraPose.tx(), cameraPose.ty(), cameraPose.tz() - 1.0f); // Ví dụ: dịch lùi 1 mét
//
//                        // Tạo một anchor tại vị trí tính toán
//                        Anchor anchor = plane.createAnchor(anchorPose);
//
//                        // Tạo đối tượng preview
//                        MaterialFactory.makeOpaqueWithColor(this, new Color(android.graphics.Color.RED))
//                                .thenAccept(material -> {
//                                    ModelRenderable modelRenderable = ShapeFactory.makeCube(
//                                            new Vector3(new Vector3(0.1f, 0.1f, 0.1f)), // Kích thước của hộp dựa trên mặt phẳng
//                                            Vector3.zero(), // Vị trí tương đối so với anchor
//                                            material); // Vật liệu
//
//                                    // Tạo AnchorNode cho đối tượng preview
//                                    AnchorNode anchorNode = new AnchorNode(anchor);
//                                    anchorNode.setRenderable(modelRenderable); // Đặt renderable cho AnchorNode
//
//                                    // Tạo TransformableNode từ AnchorNode để có thể di chuyển và scale
//                                    TransformableNode transformableNode = new TransformableNode(arFragment.getTransformationSystem());
//                                    transformableNode.setParent(anchorNode);
//                                    transformableNode.setRenderable(modelRenderable);
//                                    transformableNode.setWorldScale(new Vector3(0.3f, 0.3f, 0.3f)); // Thiết lập kích thước ban đầu
//                                    transformableNode.getScaleController().setMinScale(0.1f); // Thiết lập kích thước tối thiểu
//                                    transformableNode.getScaleController().setMaxScale(1.0f); // Thiết lập kích thước tối đa
//                                    // Thêm AnchorNode vào Scene
//                                    arFragment.getArSceneView().getScene().addChild(anchorNode);
//
//                                    // Lưu trữ AnchorNode hiện tại
//                                    previewAnchors.add(anchorNode);
//                                });
//
//                        // Chỉ xử lý mặt phẳng đầu tiên được detect
//                        break;
//                    }
//                }
            });
  }

    AnchorNode selectedAnchorNode;
//    GestureDetector gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
//        @Override
//        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
//            MotionEvent tap = MotionEvent.obtain(e2.getDownTime(), e2.getEventTime(), MotionEvent.ACTION_DOWN, e2.getX(), e2.getY(), 0);
//            Frame frame = arFragment.getArSceneView().getArFrame();
//            if (frame != null) {
//                List<HitResult> hitResults = frame.hitTest(tap);
//                if (!hitResults.isEmpty()) {
//                    HitResult hitResult = hitResults.get(0);
//                    Anchor newAnchor = hitResult.createAnchor();
//                    if (selectedAnchorNode != null) {
//                        selectedAnchorNode.setAnchor(newAnchor);
//                    }
//                }
//            }
//            return true;
//        }
//    });

    private void updateLine(LineRenderable line) {
        Vector3 start = line.startNode.getWorldPosition();
        Vector3 end = line.endNode.getWorldPosition();
        float distance = Vector3.subtract(end, start).length();

        // Cập nhật vị trí và độ dài của đoạn thẳng
        line.lineNode.setWorldPosition(Vector3.add(start, end).scaled(.5f));
        line.lineNode.setWorldRotation(Quaternion.lookRotation(Vector3.subtract(end, start), Vector3.up()));

        // Tạo lại hình trụ với chiều dài mới
        MaterialFactory.makeOpaqueWithColor(this, new Color(android.graphics.Color.RED))
                .thenAccept(material -> {

                    ModelRenderable modelRenderable = ShapeFactory.makeCube(
                            new Vector3(.01f, .01f, distance),
                            Vector3.zero(), material);

                    AnchorNode lineNode = new AnchorNode();
                    lineNode.setRenderable(modelRenderable);
                    lineNode.setParent(arFragment.getArSceneView().getScene());
                    lineNode.setWorldPosition(Vector3.add(start, end).scaled(.5f));
                    lineNode.setWorldRotation(Quaternion.lookRotation(Vector3.subtract(end, start), Vector3.up()));

                    // Lưu thông tin về đoạn thẳng
                    line.lineNode.setRenderable(modelRenderable);

                    // Tạo AnchorNode cho BillboardNode (văn bản)
                    AnchorNode textNode = new AnchorNode();
                    textNode.setParent(arFragment.getArSceneView().getScene());
                    textNode.setWorldPosition(Vector3.add(start, end).scaled(.5f)); // Đặt vị trí giữa hai anchor

                    // Tạo BillboardNode để chứa văn bản
                    ViewRenderable.builder()
                            .setView(this, R.layout.text_layout) // layout của văn bản
                            .build()
                            .thenAccept(viewRenderable -> {
                                // Đặt văn bản vào BillboardNode
                                Node textView = new Node();
                                textView.setParent(textNode);
                                textView.setRenderable(viewRenderable);

                                // Đặt văn bản cho khoảng cách giữa hai anchor
                                TextView distanceTextView = viewRenderable.getView().findViewById(R.id.distanceTextView);
                                distanceTextView.setText(String.format("%.2f meters", distance));
                            });
                });
    }

    private void removeAllAnchorNodes() {
        // Lặp qua tất cả các node trong Scene và xoá các AnchorNode
        for (Node node : previewAnchors) {
            if (node instanceof AnchorNode) {
                arFragment.getArSceneView().getScene().removeChild(node);
                ((AnchorNode) node).getAnchor().detach(); // Detach anchor để giải phóng tài nguyên
                node.setParent(null); // Xóa parent của node
            }
        }
    }

    private void drawLineWithText(Anchor anchor1, Anchor anchor2) {
        Vector3 start = new AnchorNode(anchor1).getWorldPosition();
        Vector3 end = new AnchorNode(anchor2).getWorldPosition();
        // Tính toán khoảng cách giữa hai anchor
        float distance = Vector3.subtract(end, start).length();

        // Tạo AnchorNode cho hai anchor
        AnchorNode anchorNode1 = new AnchorNode(anchor1);
        AnchorNode anchorNode2 = new AnchorNode(anchor2);

        // Thêm các node vào Scene
        arFragment.getArSceneView().getScene().addChild(anchorNode1);
        arFragment.getArSceneView().getScene().addChild(anchorNode2);

        // Tạo TransformableNode để cho phép di chuyển các anchor
        TransformableNode transformableNode1 = new TransformableNode(arFragment.getTransformationSystem());
        transformableNode1.setParent(anchorNode1);
        arFragment.getArSceneView().getScene().addChild(transformableNode1);

        TransformableNode transformableNode2 = new TransformableNode(arFragment.getTransformationSystem());
        transformableNode2.setParent(anchorNode2);
        arFragment.getArSceneView().getScene().addChild(transformableNode2);


        // Tạo AnchorNode cho đoạn thẳng
        MaterialFactory.makeOpaqueWithColor(this, new Color(android.graphics.Color.RED))
                .thenAccept(
                        material -> {
                            ModelRenderable modelRenderable = ShapeFactory.makeCube(
                                    new Vector3(.01f, .01f, distance),
                                    Vector3.zero(), material);

                            AnchorNode lineNode = new AnchorNode();
                            lineNode.setRenderable(modelRenderable);
                            lineNode.setParent(arFragment.getArSceneView().getScene());
                            lineNode.setWorldPosition(Vector3.add(start, end).scaled(.5f));
                            lineNode.setWorldRotation(Quaternion.lookRotation(Vector3.subtract(end, start), Vector3.up()));

                            // Lưu thông tin về đoạn thẳng
                            LineRenderable line = new LineRenderable(anchorNode1, anchorNode2, lineNode, modelRenderable);
                            lines.add(line);

                            // Tạo AnchorNode cho BillboardNode (văn bản)
                            AnchorNode textNode = new AnchorNode();
                            textNode.setParent(arFragment.getArSceneView().getScene());
                            textNode.setWorldPosition(Vector3.add(start, end).scaled(.5f)); // Đặt vị trí giữa hai anchor

                            // Tạo BillboardNode để chứa văn bản
                            ViewRenderable.builder()
                                    .setView(this, R.layout.text_layout) // layout của văn bản
                                    .build()
                                    .thenAccept(viewRenderable -> {
                                        // Đặt văn bản vào BillboardNode
                                        Node textView = new Node();
                                        textView.setParent(textNode);
                                        textView.setRenderable(viewRenderable);

                                        // Đặt văn bản cho khoảng cách giữa hai anchor
                                        TextView distanceTextView = viewRenderable.getView().findViewById(R.id.distanceTextView);
                                        distanceTextView.setText(String.format("%.2f meters", distance));
                                    });

                            // Lắng nghe sự kiện di chuyển của TransformableNode
                            transformableNode1.setOnTapListener((hitTestResult, motionEvent) -> updateLine(line));
                            transformableNode2.setOnTapListener((hitTestResult, motionEvent) -> updateLine(line));
                        });
    }

    private void addModelToScene(Anchor anchor, ModelRenderable modelRenderable) {
        AnchorNode anchorNode = new AnchorNode(anchor);
        TransformableNode transformableNode = new TransformableNode(arFragment.getTransformationSystem());
        transformableNode.setParent(anchorNode);
        transformableNode.setRenderable(modelRenderable);
        transformableNode.getScaleController().setMinScale(0.1f); // Thiết lập kích thước tối thiểu
        transformableNode.getScaleController().setMaxScale(1.0f); // Thiết lập kích thước tối đa
        transformableNode.setWorldScale(new Vector3(0.5f, 0.5f, 0.5f)); // Thiết lập kích thước ban đầu
        arFragment.getArSceneView().getScene().addChild(anchorNode);
        transformableNode.select();
    }

    private void drawLine(Anchor anchor1, Anchor anchor2) {
        Vector3 point1 = new AnchorNode(anchor1).getWorldPosition();
        Vector3 point2 = new AnchorNode(anchor2).getWorldPosition();

        MaterialFactory.makeOpaqueWithColor(this, new Color(android.graphics.Color.RED))
                .thenAccept(
                        material -> {
                            ModelRenderable modelRenderable = ShapeFactory.makeCube(
                                    new Vector3(.01f, .01f, Vector3.subtract(point1, point2).length()),
                                    Vector3.zero(), material);

                            AnchorNode node = new AnchorNode();
                            node.setRenderable(modelRenderable);
                            node.setWorldPosition(Vector3.add(point1, point2).scaled(.5f));
                            node.setWorldRotation(Quaternion.lookRotation(Vector3.subtract(point2, point1), Vector3.up()));

                            arFragment.getArSceneView().getScene().addChild(node);
                        });
    }

    /**
   * Returns false and displays an error message if Sceneform can not run, true if Sceneform can run
   * on this device.
   *
   * <p>Sceneform requires Android N on the device as well as OpenGL 3.0 capabilities.
   *
   * <p>Finishes the activity if Sceneform can not run
   */
  public static boolean checkIsSupportedDeviceOrFinish(final Activity activity) {
    if (Build.VERSION.SDK_INT < VERSION_CODES.N) {
      Log.e(TAG, "Sceneform requires Android N or later");
      Toast.makeText(activity, "Sceneform requires Android N or later", Toast.LENGTH_LONG).show();
      activity.finish();
      return false;
    }
    String openGlVersionString =
        ((ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE))
            .getDeviceConfigurationInfo()
            .getGlEsVersion();
    if (Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
      Log.e(TAG, "Sceneform requires OpenGL ES 3.0 later");
      Toast.makeText(activity, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
          .show();
      activity.finish();
      return false;
    }
    return true;
  }
}
