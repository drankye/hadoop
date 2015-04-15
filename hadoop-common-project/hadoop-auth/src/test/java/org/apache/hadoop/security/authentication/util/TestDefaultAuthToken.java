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
package org.apache.hadoop.security.authentication.util;

import org.apache.hadoop.security.authentication.client.AuthenticationException;
import org.apache.hadoop.security.authentication.tokenauth.DefaultAuthToken;
import org.junit.Assert;
import org.junit.Test;

public class TestDefaultAuthToken {

  @Test
  public void testConstructor() throws Exception {
    try {
      new DefaultAuthToken(null, "p", "t");
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      // Expected
    } catch (Throwable ex) {
      Assert.fail();
    }
    try {
      new DefaultAuthToken("", "p", "t");
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      // Expected
    } catch (Throwable ex) {
      Assert.fail();
    }
    try {
      new DefaultAuthToken("u", null, "t");
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      // Expected
    } catch (Throwable ex) {
      Assert.fail();
    }
    try {
      new DefaultAuthToken("u", "", "t");
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      // Expected
    } catch (Throwable ex) {
      Assert.fail();
    }
    try {
      new DefaultAuthToken("u", "p", null);
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      // Expected
    } catch (Throwable ex) {
      Assert.fail();
    }
    try {
      new DefaultAuthToken("u", "p", "");
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      // Expected
    } catch (Throwable ex) {
      Assert.fail();
    }
    new DefaultAuthToken("u", "p", "t");
  }

  @Test
  public void testGetters() throws Exception {
    long expires = System.currentTimeMillis() + 50;
    DefaultAuthToken token = new DefaultAuthToken("u", "p", "t");
    token.setExpires(expires);
    Assert.assertEquals("u", token.getUserName());
    Assert.assertEquals("p", token.getName());
    Assert.assertEquals("t", token.getType());
    Assert.assertEquals(expires, token.getExpires());
    Assert.assertFalse(token.isExpired());
    Thread.sleep(70);               // +20 msec fuzz for timer granularity.
    Assert.assertTrue(token.isExpired());
  }

  @Test
  public void testToStringAndParse() throws Exception {
    long expires = System.currentTimeMillis() + 50;
    DefaultAuthToken token = new DefaultAuthToken("u", "p", "t");
    token.setExpires(expires);
    String str = token.toString();
    token = DefaultAuthToken.parse(str);
    Assert.assertEquals("p", token.getName());
    Assert.assertEquals("t", token.getType());
    Assert.assertEquals(expires, token.getExpires());
    Assert.assertFalse(token.isExpired());
    Thread.sleep(70);               // +20 msec fuzz for timer granularity.
    Assert.assertTrue(token.isExpired());
  }

  @Test
  public void testParseValidAndInvalid() throws Exception {
    long expires = System.currentTimeMillis() + 50;
    DefaultAuthToken token = new DefaultAuthToken("u", "p", "t");
    token.setExpires(expires);
    String ostr = token.toString();

    String str1 = "\"" + ostr + "\"";
    DefaultAuthToken.parse(str1);
    
    String str2 = ostr + "&s=1234";
    DefaultAuthToken.parse(str2);

    String str = ostr.substring(0, ostr.indexOf("e="));
    try {
      DefaultAuthToken.parse(str);
      Assert.fail();
    } catch (AuthenticationException ex) {
      // Expected
    } catch (Exception ex) {
      Assert.fail();
    }
  }
}
