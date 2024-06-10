package com.google.ar.sceneform.samples.gltf;

import android.content.Context;

import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ShapeFactory;

public class LineNode extends AnchorNode {
    private Node startNode;
    private Node endNode;

    public LineNode(Context context, Vector3 start, Vector3 end) {
        startNode = new Node();
        startNode.setParent(this);
        startNode.setWorldPosition(start);

        endNode = new Node();
        endNode.setParent(this);
        endNode.setWorldPosition(end);

        MaterialFactory.makeOpaqueWithColor(context, new Color(android.graphics.Color.RED))
                .thenAccept(material -> {
                    ModelRenderable model = ShapeFactory.makeCube(
                            new Vector3(.01f, .01f, Vector3.subtract(end, start).length()),
                            Vector3.zero(), material);

                    Node lineNode = new Node();
                    lineNode.setParent(this);
                    lineNode.setRenderable(model);
                    lineNode.setWorldPosition(Vector3.add(start, end).scaled(.5f));
                    lineNode.setWorldRotation(Quaternion.lookRotation(Vector3.subtract(end, start), Vector3.up()));
                });
    }
}