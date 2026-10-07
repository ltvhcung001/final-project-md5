package com.omnichannel.order.grpc;

import com.omnichannel.common.exception.AppException;
import com.omnichannel.common.exception.ErrorCode;
import com.omnichannel.product.grpc.GetProductsReply;
import com.omnichannel.product.grpc.GetProductsRequest;
import com.omnichannel.product.grpc.ProductReply;
import com.omnichannel.product.grpc.ProductServiceGrpc;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Synchronous gRPC call to the Product service for the name/price snapshot. */
@Component
public class ProductClient {

    @GrpcClient("product-service")
    private ProductServiceGrpc.ProductServiceBlockingStub stub;

    public Map<String, ProductReply> getProducts(Collection<String> skus) {
        try {
            GetProductsReply reply = stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                    .getProducts(GetProductsRequest.newBuilder().addAllSkus(skus).build());
            Map<String, ProductReply> bySku = new HashMap<>();
            reply.getProductsList().forEach(p -> bySku.put(p.getSku(), p));
            return bySku;
        } catch (StatusRuntimeException e) {
            throw new AppException(ErrorCode.UPSTREAM_UNAVAILABLE, "Product service error: " + e.getStatus().getCode());
        }
    }
}
