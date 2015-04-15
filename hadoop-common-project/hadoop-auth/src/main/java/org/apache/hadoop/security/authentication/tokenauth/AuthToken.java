/**
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
 * limitations under the License. See accompanying LICENSE file.
 */
package org.apache.hadoop.security.authentication.tokenauth;

/**
 * A generic AuthToken representation and API.
 */
public interface AuthToken {

  /**
   * Returns the expiration time of the token.
   *
   * @return the expiration time of the token, in milliseconds since Epoc.
   */
  public long getExpires();

  /**
   * Sets the expiration of the token.
   *
   * @param expires expiration time of the token in milliseconds since the epoch.
   */
  public void setExpires(long expires);


  /**
   * Returns true if the token has expired.
   *
   * @return true if the token has expired.
   */
  public boolean isExpired();

  /**
   * Returns the user name.
   *
   * @return the user name.
   */
  public String getUserName();

  /**
   * Returns the authentication mechanism of the token.
   *
   * @return the authentication mechanism of the token.
   */
  public String getType();

}
