/*
 * Copyright 2014 mango.concurrent.cc
 *
 * The Mango Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package cc.concurrent.mango;

import cc.concurrent.mango.support.Config;
import cc.concurrent.mango.support.Randoms;
import cc.concurrent.mango.support.Tables;
import cc.concurrent.mango.support.model4table.Msg;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import org.junit.Before;
import org.junit.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * 测试分表
 *
 * @author ash
 */
public class TablePartitionTest {


    private final static DataSource ds = Config.getDataSource();
    private final static Mango mango = new Mango(ds);
    private final static MsgDao dao = mango.create(MsgDao.class);

    @Before
    public void before() throws Exception {
        Connection conn = ds.getConnection();
        Tables.MSG_PARTITION.load(conn);
        conn.close();
    }

    @Test
    public void testRandomPartition() {
        MsgDao dao = mango.create(MsgDao.class);
        int num = 10;
        List<Msg> msgs = Msg.createRandomMsgs(num);
        for (Msg msg : msgs) {
            int id = dao.insert(msg);
            assertThat(id, greaterThan(0));
            msg.setId(id);
        }
        check(msgs);
        for (Msg msg : msgs) {
            msg.setContent(Randoms.randomString(20));
        }
        dao.batchUpdate(msgs);
        check(msgs);
    }

    @Test
    public void testOnePartition() {
        MsgDao dao = mango.create(MsgDao.class);
        int num = 10;
        int uid = 100;
        List<Msg> msgs = new ArrayList<Msg>();
        for (int i = 0; i < num; i++) {
            Msg msg = new Msg();
            msg.setUid(uid);
            msg.setContent(Randoms.randomString(20));
            msgs.add(msg);
            int id = dao.insert(msg);
            msg.setId(id);
        }
        check(msgs);
        for (Msg msg : msgs) {
            msg.setContent(Randoms.randomString(20));
        }
        dao.batchUpdate(msgs);
        check(msgs);
    }

    private void check(List<Msg> msgs) {
        List<Msg> dbMsgs = new ArrayList<Msg>();
        Multiset<Integer> ms = HashMultiset.create();
        for (Msg msg : msgs) {
            ms.add(msg.getUid());
        }
        for (Multiset.Entry<Integer> entry : ms.entrySet()) {
            dbMsgs.addAll(dao.getMsgs(entry.getElement()));
        }
        assertThat(dbMsgs, hasSize(msgs.size()));
        assertThat(dbMsgs, containsInAnyOrder(msgs.toArray()));
    }

    @DB(table = "msg", tablePartition = ModTenTablePartition.class)
    interface MsgDao {

        @ReturnGeneratedId
        @SQL("insert into #table(uid, content) values(:1.uid, :1.content)")
        int insert(@ShardBy("uid") Msg msg);

        @SQL("update #table set content=:1.content where id=:1.id and uid=:1.uid")
        public int[] batchUpdate(@ShardBy("uid") List<Msg> msgs);

        @SQL("select id, uid, content from #table where uid=:1")
        public List<Msg> getMsgs(@ShardBy int uid);

    }

}
