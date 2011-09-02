/*
 * Copyright 2011 Peter Abeles
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package boofcv.abst.feature.detect.intensity;

import boofcv.abst.filter.blur.impl.MedianImageFilter;
import boofcv.alg.feature.detect.intensity.MedianCornerIntensity;
import boofcv.struct.QueueCorner;
import boofcv.struct.image.ImageBase;
import boofcv.struct.image.ImageFloat32;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Wrapper around children of {@link boofcv.alg.feature.detect.intensity.MedianCornerIntensity}.  This is a bit of a hack since
 * the median image is not provided as a standard input so it has to compute it internally
 * 
 * @author Peter Abeles
 */
public class WrapperMedianCornerIntensity<I extends ImageBase, D extends ImageBase> implements GeneralFeatureIntensity<I,D> {

	ImageFloat32 intensity = new ImageFloat32(1,1);
	Method m;
	MedianImageFilter<I> medianFilter;
	I medianImage;

	public WrapperMedianCornerIntensity(MedianImageFilter<I> medianFilter ,
										Class<I> imageType ) {
		this.medianFilter = medianFilter;
		try {
			m = MedianCornerIntensity.class.getMethod("process",ImageFloat32.class,imageType,imageType);
		} catch (NoSuchMethodException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void process(I input, D derivX , D derivY , D derivXX , D derivYY , D derivXY ) {
		intensity.reshape(input.width,input.height);
		if( medianImage == null ) {
			medianImage = (I)input._createNew(input.width,input.height);
		} else {
			medianImage.reshape(input.width,input.height);
		}
		
		medianFilter.process(input,medianImage);
		try {
			m.invoke(null,intensity,input,medianImage);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		} catch (InvocationTargetException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public ImageFloat32 getIntensity() {
		return intensity;
	}

	@Override
	public QueueCorner getCandidates() {
		return null;
	}

	@Override
	public boolean getRequiresGradient() {
		return false;
	}

	@Override
	public boolean getRequiresHessian() {
		return false;
	}

	@Override
	public boolean hasCandidates() {
		return false;
	}
}