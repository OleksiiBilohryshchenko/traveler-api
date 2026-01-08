package ua.sumdu.dds.travelerapi.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.RollbackException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ua.sumdu.dds.travelerapi.model.TravelPlan;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TravelPlanOptimisticLockIT {

    @Autowired
    private EntityManagerFactory emf;

    @Test
    void shouldFailOnOptimisticLockConflict() {

        EntityManager em1 = emf.createEntityManager();
        EntityManager em2 = emf.createEntityManager();

        try {
            // create
            em1.getTransaction().begin();
            TravelPlan plan = TravelPlan.builder()
                    .title("Initial")
                    .build();
            em1.persist(plan);
            em1.getTransaction().commit();

            // load in two contexts
            TravelPlan first = em1.find(TravelPlan.class, plan.getId());
            TravelPlan second = em2.find(TravelPlan.class, plan.getId());

            // first update OK
            em1.getTransaction().begin();
            first.setTitle("First update");
            em1.getTransaction().commit();

            // second update → conflict
            em2.getTransaction().begin();
            second.setTitle("Second update");

            RollbackException ex = assertThrows(
                    RollbackException.class,
                    () -> em2.getTransaction().commit()
            );

            assertTrue(
                    ex.getCause() instanceof OptimisticLockException,
                    "Expected OptimisticLockException as cause"
            );

        } finally {
            em1.close();
            em2.close();
        }
    }
}
