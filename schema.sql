--
-- PostgreSQL database dump
--

\restrict bhGzpO6E55hXGP8FEHGYaUGwvNPqTgd1LBL5tgCq5tpQIvjSxC6EfgGGlj8h7z4

-- Dumped from database version 18.1
-- Dumped by pg_dump version 18.1

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: pgcrypto; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;


--
-- Name: EXTENSION pgcrypto; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION pgcrypto IS 'cryptographic functions';


--
-- Name: assign_location_order(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.assign_location_order() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.visit_order IS NULL THEN
SELECT COALESCE(MAX(visit_order), 0) + 1
INTO NEW.visit_order
FROM locations
WHERE travel_plan_id = NEW.travel_plan_id;
END IF;

RETURN NEW;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: locations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.locations (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    travel_plan_id uuid NOT NULL,
    name character varying(200) NOT NULL,
    address text,
    latitude numeric(10,6),
    longitude numeric(11,6),
    visit_order integer,
    arrival_date timestamp with time zone,
    departure_date timestamp with time zone,
    budget numeric(10,2),
    notes text,
    created_at timestamp with time zone DEFAULT now(),
    version integer DEFAULT 1 NOT NULL,
    CONSTRAINT check_coordinates_lat CHECK (((latitude IS NULL) OR ((latitude >= ('-90'::integer)::numeric) AND (latitude <= (90)::numeric)))),
    CONSTRAINT check_coordinates_lng CHECK (((longitude IS NULL) OR ((longitude >= ('-180'::integer)::numeric) AND (longitude <= (180)::numeric)))),
    CONSTRAINT check_location_budget CHECK (((budget IS NULL) OR (budget >= (0)::numeric))),
    CONSTRAINT check_location_dates CHECK (((departure_date IS NULL) OR (arrival_date IS NULL) OR (departure_date >= arrival_date))),
    CONSTRAINT locations_name_check CHECK ((length(TRIM(BOTH FROM name)) > 0)),
    CONSTRAINT locations_visit_order_check CHECK ((visit_order > 0))
);


--
-- Name: travel_plans; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.travel_plans (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    title character varying(200) NOT NULL,
    description text,
    start_date date,
    end_date date,
    budget numeric(10,2),
    currency character varying(3) DEFAULT 'USD'::character varying,
    is_public boolean DEFAULT false,
    version integer DEFAULT 1 NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    CONSTRAINT check_budget CHECK (((budget IS NULL) OR (budget >= (0)::numeric))),
    CONSTRAINT check_dates CHECK (((end_date IS NULL) OR (start_date IS NULL) OR (end_date >= start_date))),
    CONSTRAINT travel_plans_currency_check CHECK ((length((currency)::text) = 3)),
    CONSTRAINT travel_plans_title_check CHECK ((length(TRIM(BOTH FROM title)) > 0))
);


--
-- Name: locations locations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.locations
    ADD CONSTRAINT locations_pkey PRIMARY KEY (id);


--
-- Name: travel_plans travel_plans_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.travel_plans
    ADD CONSTRAINT travel_plans_pkey PRIMARY KEY (id);


--
-- Name: locations unique_plan_order; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.locations
    ADD CONSTRAINT unique_plan_order UNIQUE (travel_plan_id, visit_order);


--
-- Name: idx_locations_plan_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_locations_plan_order ON public.locations USING btree (travel_plan_id, visit_order);


--
-- Name: locations set_location_order; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER set_location_order BEFORE INSERT ON public.locations FOR EACH ROW EXECUTE FUNCTION public.assign_location_order();


--
-- Name: locations locations_travel_plan_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.locations
    ADD CONSTRAINT locations_travel_plan_id_fkey FOREIGN KEY (travel_plan_id) REFERENCES public.travel_plans(id) ON DELETE CASCADE;


--
-- Name: traveler_publication; Type: PUBLICATION; Schema: -; Owner: -
--

CREATE PUBLICATION traveler_publication FOR ALL TABLES WITH (publish = 'insert, update, delete, truncate');


--
-- PostgreSQL database dump complete
--

\unrestrict bhGzpO6E55hXGP8FEHGYaUGwvNPqTgd1LBL5tgCq5tpQIvjSxC6EfgGGlj8h7z4

