// Copyright (C) 2014 Guibing Guo
//
// This file is part of LibRec.
//
// LibRec is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// LibRec is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with LibRec. If not, see <http://www.gnu.org/licenses/>.
//

package i5.las2peer.services.recommender.librec.data;

/**
 * An entry of a matrix.
 */
public interface MatrixEntry {

	/**
	 * Returns the current row index
	 * @return row index
	 */
	int row();

	/**
	 * Returns the current column index
	 * @return column index
	 */
	int column();

	/**
	 * Returns the value at the current index
	 * @return value at the current index
	 */
	double get();

	/**
	 * Sets the value at the current index
	 * @param value value to set
	 */
	void set(double value);

}
