/**
 *   Copyright (c) Daniel Rune Jensen, Thomas Stig Jacobsen and
 *   Søren Kejser Jensen. All rights reserved.
 *   The use and distribution terms for this software are covered by the
 *   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 *   which can be found in the file epl-v10.html at the root of this distribution.
 *   By using this software in any fashion, you are agreeing to be bound by
 * 	 the terms of this license.
 *   You must not remove this notice, or any other, from this software.
 **/

package clojure.lang;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

public class TC {

static public void stmBlocking(Object refs, IFn fn, ISeq args, boolean blockOnAll) throws InterruptedException {
    HashSet<Ref> convertedRefs = null;
    if (refs instanceof Ref) {
        convertedRefs = new HashSet<Ref>();
        convertedRefs.add((Ref) refs);
    } else if (refs instanceof Collection) {
        convertedRefs = new HashSet<Ref>((Collection) refs);
    }
    IOLockingTransaction transaction = IOLockingTransaction.getEx();
    transaction.doBlocking(convertedRefs, fn, args, blockOnAll);
}

static public Object stmOrElse(boolean orElseOnRetryEx, ISeq body) {
    ArrayList<IFn> fns = new ArrayList<IFn>((Collection) body);
    if (fns.isEmpty()) {
        return null;
    }
	IOLockingTransaction transaction = IOLockingTransaction.getEx();
	return transaction.doOrElse(orElseOnRetryEx, fns);
}

static public void stmAbort() throws Exception {
	IOLockingTransaction transaction = IOLockingTransaction.getEx();
    transaction.abort();
}
}
