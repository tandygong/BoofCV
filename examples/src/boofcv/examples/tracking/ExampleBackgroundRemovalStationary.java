/*
 * Copyright (c) 2011-2015, Peter Abeles. All Rights Reserved.
 *
 * This file is part of BoofCV (http://boofcv.org).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package boofcv.examples.tracking;

import boofcv.alg.background.BackgroundModelStationary;
import boofcv.alg.background.stationary.BackgroundStationaryBasic_SB;
import boofcv.alg.misc.ImageStatistics;
import boofcv.gui.image.ImageBinaryPanel;
import boofcv.gui.image.ShowImages;
import boofcv.io.MediaManager;
import boofcv.io.image.SimpleImageSequence;
import boofcv.io.wrapper.DefaultMediaManager;
import boofcv.struct.image.ImageBase;
import boofcv.struct.image.ImageFloat32;
import boofcv.struct.image.ImageUInt8;
import georegression.struct.homography.Homography2D_F32;

/**
 * @author Peter Abeles
 */
// TODO simplify the creation image motion estimation
// TODO Visualization.  Show input image in a window,  Difference + color in another
public class ExampleBackgroundRemovalStationary {
	public static void main(String[] args) {


		BackgroundModelStationary background =
				new BackgroundStationaryBasic_SB(0.05f,30,ImageFloat32.class);
//				new BackgroundMovingBasic_MS(0.1f,30,new PointTransformHomography_F32(),
//						TypeInterpolate.BILINEAR, ImageType.ms(3, ImageFloat32.class));
//		BackgroundMovingGaussian background =
//				new BackgroundMovingGaussian_SB(0.01f,10,new PointTransformHomography_F32(),
//						TypeInterpolate.BILINEAR, ImageType.single(ImageFloat32.class));
//				new BackgroundMovingGaussian_MS(0.01f,40,new PointTransformHomography_F32(),
//						TypeInterpolate.BILINEAR, ImageType.ms(3, ImageFloat32.class));
//		background.setInitialVariance(64);

		MediaManager media = DefaultMediaManager.INSTANCE;
		String fileName = "../data/applet/shake.mjpeg";
		SimpleImageSequence video = media.openVideo(fileName, background.getImageType());

		ImageUInt8 segmented = new ImageUInt8(1,1);

		ImageBinaryPanel gui = null;

		while( video.hasNext() ) {
			ImageBase input = video.next();

			if( segmented.width != input.width ) {
				segmented.reshape(input.width,input.height);
				Homography2D_F32 homeToWorld = new Homography2D_F32();
				homeToWorld.a13 = input.width/2;
				homeToWorld.a23 = input.height/2;

				gui = new ImageBinaryPanel(segmented);
				ShowImages.showWindow(gui,"Detections",true);
			}

			background.segment(input,segmented);
			background.updateBackground(input);

			System.out.println("sum = "+ ImageStatistics.sum(segmented)+" "+ImageStatistics.max(segmented));
			gui.setBinaryImage(segmented);
			gui.repaint();
			System.out.println("Processed!!");

			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {

			}
		}
	}
}