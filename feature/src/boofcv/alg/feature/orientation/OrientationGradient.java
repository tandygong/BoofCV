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

package boofcv.alg.feature.orientation;

import boofcv.struct.image.ImageBase;


/**
 * Estimates the orientation of a region from the image gradient.
 *
 * @author Peter Abeles
 */
public interface OrientationGradient<D extends ImageBase> extends RegionOrientation {

	/**
	 * Specifies input image data for estimating orientation.
	 *
	 * @param derivX Image derivative along x-axis.
	 * @param derivY Image derivative along y-axis.
	 */
	public void setImage( D derivX , D derivY );

	/**
	 * Returns the type of image it can process.
	 *
	 * @return Type of image which can be processed
	 */
	public Class<D> getImageType();
}