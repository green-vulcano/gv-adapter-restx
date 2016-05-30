package it.greenvulcano.gvesb.virtual.rest;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import it.greenvulcano.gvesb.virtual.OperationFactory;

public class Activator implements BundleActivator {

	@Override
	public void start(BundleContext context) throws Exception {
		OperationFactory.registerSupplier("rest-call", RestCallOperation::new);

	}

	@Override
	public void stop(BundleContext context) throws Exception {
		OperationFactory.unregisterSupplier("rest-call");

	}

}
