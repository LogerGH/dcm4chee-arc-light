/*
 * **** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * J4Care.
 * Portions created by the Initial Developer are Copyright (C) 2015-2019
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * **** END LICENSE BLOCK *****
 *
 */

package org.dcm4chee.arc.retrieve.mgt.impl;

import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.query.util.MatchTask;
import org.dcm4chee.arc.query.util.TaskQueryParam;
import org.dcm4chee.arc.retrieve.mgt.RetrieveTaskQuery;
import org.hibernate.annotations.QueryHints;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.*;
import java.util.Iterator;
import java.util.stream.Stream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Mar 2018
 */
class RetrieveTaskQueryImpl implements RetrieveTaskQuery {
    private Join<RetrieveTask, QueueMessage> queueMsg;
    private Root<RetrieveTask> retrieveTask;
    private Stream<RetrieveTask> resultStream;
    private Iterator<RetrieveTask> results;

    private final MatchTask matchTask;
    private final TaskQueryParam queueTaskQueryParam;
    private final TaskQueryParam retrieveTaskQueryParam;
    private final EntityManager em;
    private final CriteriaBuilder cb;

    public RetrieveTaskQueryImpl(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam retrieveTaskQueryParam, EntityManager em) {
        this.em = em;
        this.cb = em.getCriteriaBuilder();
        this.matchTask = new MatchTask(cb);
        this.queueTaskQueryParam = queueTaskQueryParam;
        this.retrieveTaskQueryParam = retrieveTaskQueryParam;
    }

    @Override
    public void beginTransaction() {}

    @Override
    public void executeQuery(int fetchSize, int offset, int limit) {
        close(resultStream);
        TypedQuery<RetrieveTask> query = em.createQuery(select())
                .setHint(QueryHints.FETCH_SIZE, fetchSize);
        if (offset > 0)
            query.setFirstResult(offset);
        if (limit > 0)
            query.setMaxResults(limit);
        resultStream = query.getResultStream();
        results = resultStream.iterator();
    }

    @Override
    public long fetchCount() {
        return em.createQuery(count()).getSingleResult();
    }

    private CriteriaQuery<Long> count() {
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        retrieveTask = q.from(RetrieveTask.class);
        return createQuery(q, null, retrieveTask, cb.count(retrieveTask));
    }

    private <X> CriteriaQuery<Long> createQuery(CriteriaQuery<Long> q, Expression<Boolean> x,
                                                From<X, RetrieveTask> retrieveTask, Expression<Long> longExpression) {
        queueMsg = retrieveTask.join(RetrieveTask_.queueMessage);
        q = q.select(longExpression);
        Expression<Boolean> queueMsgPredicate = matchTask.matchQueueMsg(x, queueTaskQueryParam, queueMsg);
        Expression<Boolean> retrieveTaskPredicate = matchTask.matchRetrieveTask(x, retrieveTaskQueryParam, retrieveTask);
        if (queueMsgPredicate != null)
            q = q.where(queueMsgPredicate);
        if (retrieveTaskPredicate != null)
            q = q.where(retrieveTaskPredicate);
        return q;
    }

    private CriteriaQuery<RetrieveTask> select() {
        CriteriaQuery<RetrieveTask> q = cb.createQuery(RetrieveTask.class);
        retrieveTask = q.from(RetrieveTask.class);
        queueMsg = retrieveTask.join(RetrieveTask_.queueMessage);
        q = q.select(retrieveTask);
        Expression<Boolean> queueMsgPredicate = matchTask.matchQueueMsg(null, queueTaskQueryParam, queueMsg);
        Expression<Boolean> retrieveTaskPredicate = matchTask.matchRetrieveTask(null, retrieveTaskQueryParam, retrieveTask);
        if (queueMsgPredicate != null)
            q = q.where(queueMsgPredicate);
        if (retrieveTaskPredicate != null)
            q = q.where(retrieveTaskPredicate);
        if (retrieveTaskQueryParam.getOrderBy() != null)
            q = q.orderBy(matchTask.retrieveTaskOrder(retrieveTaskQueryParam.getOrderBy(), retrieveTask));
        return q;
    }

    private void close(Stream<RetrieveTask> resultStream) {
        if (resultStream != null)
            resultStream.close();
    }

    @Override
    public boolean hasMoreMatches() {
        return results.hasNext();
    }

    @Override
    public RetrieveTask nextMatch() {
        return results.next();
    }

    @Override
    public void close() {}
}
