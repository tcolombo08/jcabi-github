/**
 * Copyright (c) 2012-2013, JCabi.com
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met: 1) Redistributions of source code must retain the above
 * copyright notice, this list of conditions and the following
 * disclaimer. 2) Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided
 * with the distribution. 3) Neither the name of the jcabi.com nor
 * the names of its contributors may be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT
 * NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.jcabi.github;

import com.jcabi.aspects.Immutable;
import com.rexsl.test.Request;
import com.rexsl.test.response.JsonResponse;
import com.rexsl.test.response.RestResponse;
import com.rexsl.test.response.WebLinkingResponse;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Queue;
import javax.json.JsonObject;
import lombok.EqualsAndHashCode;

/**
 * Github pagination.
 *
 * @author Yegor Bugayenko (yegor@tpc2.com)
 * @version $Id$
 * @since 0.4
 * @param <T> Type of iterable objects
 * @see <a href="http://developer.github.com/v3/#pagination">Pagination</a>
 */
@Immutable
@EqualsAndHashCode(of = { "entry", "map" })
final class RtPagination<T> implements Iterable<T> {

    /**
     * Mapping to use.
     */
    private final transient RtPagination.Mapping<T> map;

    /**
     * Start entry to use.
     */
    private final transient Request entry;

    /**
     * Public ctor.
     * @param req Request
     * @param mpp Mapping
     */
    RtPagination(final Request req, final RtPagination.Mapping<T> mpp) {
        this.entry = req;
        this.map = mpp;
    }

    @Override
    public String toString() {
        return this.entry.uri().get().toString();
    }

    @Override
    public Iterator<T> iterator() {
        return new RtPagination.Items<T>(this.entry, this.map);
    }

    /**
     * Entry.
     * @return Entry point
     */
    public Request request() {
        return this.entry;
    }

    /**
     * Mapping.
     * @return Mapping
     */
    public RtPagination.Mapping<T> mapping() {
        return this.map;
    }

    /**
     * Mapping from JsonObject to the destination type.
     * @param <X> Type of custom object
     */
    @Immutable
    public interface Mapping<X> {
        /**
         * Map JsonObject to the type required.
         * @param object JsonObject
         * @return Custom object
         */
        X map(JsonObject object);
    }

    /**
     * Iterator.
     */
    @EqualsAndHashCode(of = { "mapping", "request", "objects", "hasMore" })
    private static final class Items<X> implements Iterator<X> {
        /**
         * Mapping to use.
         */
        private final transient RtPagination.Mapping<X> mapping;
        /**
         * Next entry to use.
         */
        private transient Request request;
        /**
         * Available objects.
         */
        private transient Queue<JsonObject> objects;
        /**
         * Current entry can be used to fetch objects.
         */
        private transient boolean hasMore = true;
        /**
         * Ctor.
         * @param entry Entry
         * @param mpp Mapping
         */
        Items(final Request entry, final RtPagination.Mapping<X> mpp) {
            this.request = entry;
            this.mapping = mpp;
            this.objects = new LinkedList<JsonObject>();
        }
        @Override
        public X next() {
            synchronized (this.mapping) {
                if (!this.hasNext()) {
                    throw new NoSuchElementException(
                        "no more elements in pagination, use #hasNext()"
                    );
                }
                return this.mapping.map(this.objects.remove());
            }
        }
        @Override
        public void remove() {
            throw new UnsupportedOperationException("#remove()");
        }
        @Override
        public boolean hasNext() {
            synchronized (this.mapping) {
                if ((this.objects == null || this.objects.isEmpty())
                    && this.hasMore) {
                    try {
                        this.fetch();
                    } catch (IOException ex) {
                        throw new IllegalStateException(ex);
                    }
                }
                return !this.objects.isEmpty();
            }
        }
        /**
         * Fetch the next portion, if available.
         * @throws IOException If there is any I/O problem
         */
        private void fetch() throws IOException {
            final RestResponse response = this.request.fetch()
                .as(RestResponse.class)
                .assertStatus(HttpURLConnection.HTTP_OK);
            final WebLinkingResponse.Link link = response
                .as(WebLinkingResponse.class)
                .links()
                .get("next");
            if (link == null) {
                this.hasMore = false;
            } else {
                this.request = response.jump(link.uri());
            }
            this.objects = new LinkedList<JsonObject>(
                response.as(JsonResponse.class).json()
                    .readArray().getValuesAs(JsonObject.class)
            );
        }
    }

}
