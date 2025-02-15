/*
 * Copyright 2006 Open Source Applications Foundation
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.unitedinternet.cosmo.dao.hibernate;

import javax.validation.ConstraintViolationException;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.unitedinternet.cosmo.dao.UserDao;
import org.unitedinternet.cosmo.model.CalendarCollectionStamp;
import org.unitedinternet.cosmo.model.CollectionItem;
import org.unitedinternet.cosmo.model.ContentItem;
import org.unitedinternet.cosmo.model.EventExceptionStamp;
import org.unitedinternet.cosmo.model.EventStamp;
import org.unitedinternet.cosmo.model.MessageStamp;
import org.unitedinternet.cosmo.model.NoteItem;
import org.unitedinternet.cosmo.model.Stamp;
import org.unitedinternet.cosmo.model.User;
import org.unitedinternet.cosmo.model.hibernate.EntityConverter;
import org.unitedinternet.cosmo.model.hibernate.HibCalendarCollectionStamp;
import org.unitedinternet.cosmo.model.hibernate.HibEventExceptionStamp;
import org.unitedinternet.cosmo.model.hibernate.HibEventStamp;
import org.unitedinternet.cosmo.model.hibernate.HibMessageStamp;
import org.unitedinternet.cosmo.model.hibernate.HibNoteItem;
import org.unitedinternet.cosmo.model.hibernate.HibQName;
import org.unitedinternet.cosmo.model.hibernate.HibStringAttribute;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.validate.ValidationException;

/**
 * Test for hibernate content dao stamping.
 * @author ccoman
 *
 */
public class HibernateContentDaoStampingTest extends AbstractSpringDaoTestCase {
    
    private static final Logger LOG = LoggerFactory.getLogger(HibernateContentDaoStampingTest.class);
    
    @Autowired
    private UserDaoImpl userDao;
    @Autowired
    private ContentDaoImpl contentDao;

    

    /**
     * Constructor.
     */
    public HibernateContentDaoStampingTest() {
        super();
    }

    /**
     * Test stamps create.
     * @throws Exception - if something is wrong this exception is thrown.
     */
    @Test
    public void testStampsCreate() throws Exception {
        User user = getUser(userDao, "testuser");
        CollectionItem root = (CollectionItem) contentDao.getRootItem(user);

        NoteItem item = generateTestContent();
        
        item.setIcalUid("icaluid");
        item.setBody("this is a body");
        
        MessageStamp message = new HibMessageStamp(item);
        message.setBcc("bcc");
        message.setTo("to");
        message.setFrom("from");
        message.setCc("cc");
        
        EventStamp event = new HibEventStamp();
        event.setEventCalendar(helper.getCalendar("cal1.ics"));
        
        item.addStamp(message);
        item.addStamp(event);
        
        ContentItem newItem = contentDao.createContent(root, item);
        clearSession();

        ContentItem queryItem = (ContentItem) contentDao.findItemByUid(newItem.getUid());
        assertEquals(2, queryItem.getStamps().size());
        
        Stamp stamp = queryItem.getStamp(EventStamp.class);
        assertNotNull(stamp.getCreationDate());
        assertNotNull(stamp.getModifiedDate());
        assertTrue(stamp.getCreationDate().equals(stamp.getModifiedDate()));
        assertTrue(stamp instanceof EventStamp);
        assertEquals("event", stamp.getType());
        EventStamp es = (EventStamp) stamp;
        assertEquals(es.getEventCalendar().toString(), event.getEventCalendar()
                .toString());
        
        assertEquals("icaluid", ((NoteItem) queryItem).getIcalUid());
        assertEquals("this is a body", ((NoteItem) queryItem).getBody());
        
        stamp = queryItem.getStamp(MessageStamp.class);
        assertTrue(stamp instanceof MessageStamp);
        assertEquals("message", stamp.getType());
        MessageStamp ms = (MessageStamp) stamp;
        assertEquals(ms.getBcc(), message.getBcc());
        assertEquals(ms.getCc(), message.getCc());
        assertEquals(ms.getTo(), message.getTo());
        assertEquals(ms.getFrom(), message.getFrom());
    }
    
    /**
     * Test stamp handlers.
     * @throws Exception - if something is wrong this exception is thrown.
     */
    @Test
    public void testStampHandlers() throws Exception {
        User user = getUser(userDao, "testuser");
        CollectionItem root = (CollectionItem) contentDao.getRootItem(user);

        NoteItem item = generateTestContent();
        
        item.setIcalUid("icaluid");
        item.setBody("this is a body");
        
        HibEventStamp event = new HibEventStamp();
        Calendar calendar = helper.getCalendar("cal1.ics");
        event.setEventCalendar(calendar);
        
        item.addStamp(event);
        
        assertNull(event.getTimeRangeIndex());
        
        ContentItem newItem = contentDao.createContent(root, item);
        clearSession();

        ContentItem queryItem = (ContentItem) contentDao.findItemByUid(newItem.getUid());
        
        event = (HibEventStamp) queryItem.getStamp(EventStamp.class);
        assertEquals("20050817T115000Z", event.getTimeRangeIndex().getStartDate());
        assertEquals("20050817T131500Z",event.getTimeRangeIndex().getEndDate());
        assertFalse(event.getTimeRangeIndex().getIsFloating().booleanValue());
        
        VEvent vEvent = (VEvent) calendar.getComponent(Component.VEVENT);
        vEvent.getProperties().clear();
        vEvent.getProperties().add(new DtStart(new Date("20070101")));
        event.setEventCalendar(calendar);
        
        contentDao.updateContent(queryItem);
        clearSession();
        
        queryItem = (ContentItem) contentDao.findItemByUid(newItem.getUid());
        
        event = (HibEventStamp) queryItem.getStamp(EventStamp.class);
        assertEquals("20070101", event.getTimeRangeIndex().getStartDate());
        assertEquals("20070101",event.getTimeRangeIndex().getEndDate());
        assertTrue(event.getTimeRangeIndex().getIsFloating().booleanValue());
    }
    
    /**
     * Test stamps update. 
     * @throws Exception - if something is wrong this exception is thrown.
     */
    @Test
    public void testStampsUpdate() throws Exception {
        User user = getUser(userDao, "testuser");
        CollectionItem root = (CollectionItem) contentDao.getRootItem(user);

        ContentItem item = generateTestContent();
        
        ((NoteItem) item).setBody("this is a body");
        ((NoteItem) item).setIcalUid("icaluid");
        
        MessageStamp message = new HibMessageStamp(item);
        message.setBcc("bcc");
        message.setTo("to");
        message.setFrom("from");
        message.setCc("cc");
        
        EventStamp event = new HibEventStamp();
        event.setEventCalendar(helper.getCalendar("cal1.ics"));
        
        item.addStamp(message);
        item.addStamp(event);
        
        ContentItem newItem = contentDao.createContent(root, item);
        clearSession();

        ContentItem queryItem = (ContentItem) contentDao.findItemByUid(newItem.getUid());
        assertEquals(2, queryItem.getStamps().size());
        
        Stamp stamp = queryItem.getStamp(MessageStamp.class);
        queryItem.removeStamp(stamp);
        
        stamp = queryItem.getStamp(EventStamp.class);
        EventStamp es = (EventStamp) stamp;
        queryItem.setClientModifiedDate(System.currentTimeMillis());
        es.setEventCalendar(helper.getCalendar("cal2.ics"));
        Calendar newCal = es.getEventCalendar();
        Thread.sleep(10);
        
        contentDao.updateContent(queryItem);
        
        clearSession();
        queryItem = (ContentItem) contentDao.findItemByUid(newItem.getUid());
        assertEquals(1, queryItem.getStamps().size());
        assertNull(queryItem.getStamp(MessageStamp.class));
        stamp = queryItem.getStamp(EventStamp.class);
        es = (EventStamp) stamp;
       
        assertTrue(stamp.getModifiedDate() >= stamp.getCreationDate());
        
        if(!es.getEventCalendar().toString().equals(newCal.toString())) {
            LOG.error(es.getEventCalendar().toString());
            LOG.error(newCal.toString());
        }
        assertEquals(es.getEventCalendar().toString(), newCal.toString());
    }
    
    /**
     * Test event stamp validation.
     * @throws Exception - if something is wrong this exception is thrown.
     */
    @Test
    public void testEventStampValidation() throws Exception {
        EventStamp event = new HibEventStamp();        
        assertThrows(ValidationException.class, ()-> {
            event.setEventCalendar(helper.getCalendar("noevent.ics"));
        });                
    }
    
    /**
     * Test for removing stamp.
     * @throws Exception - if something is wrong this exception is thrown.
     */
    @Test
    public void testRemoveStamp() throws Exception {
        User user = getUser(userDao, "testuser");
        CollectionItem root = (CollectionItem) contentDao.getRootItem(user);

        NoteItem item = generateTestContent();
        
        item.setIcalUid("icaluid");
        item.setBody("this is a body");
        
        EventStamp event = new HibEventStamp();
        event.setEventCalendar(helper.getCalendar("cal1.ics"));
        
        item.addStamp(event);
        
        ContentItem newItem = contentDao.createContent(root, item);
        clearSession();

        ContentItem queryItem = (ContentItem) contentDao.findItemByUid(newItem.getUid());
        assertEquals(1, queryItem.getStamps().size());
       
        Stamp stamp = queryItem.getStamp(EventStamp.class);
        queryItem.removeStamp(stamp);
        contentDao.updateContent(queryItem);
        clearSession();
        
        queryItem = (ContentItem) contentDao.findItemByUid(newItem.getUid());
        assertNotNull(queryItem);
        assertEquals(queryItem.getStamps().size(),0);
        assertEquals(1, queryItem.getTombstones().size());
        
        event = new HibEventStamp();
        event.setEventCalendar(helper.getCalendar("cal1.ics"));
        queryItem.addStamp(event);
        
        contentDao.updateContent(queryItem);
        clearSession();
        
        queryItem = (ContentItem) contentDao.findItemByUid(newItem.getUid());
        assertEquals(1, queryItem.getStamps().size());
    }
    
    /**
     * test calendar collection stamp.
     * @throws Exception - if something is wrong this exception is thrown.
     */
    @Test
    public void testCalendarCollectionStamp() throws Exception {
        User user = getUser(userDao, "testuser");
        CollectionItem root = (CollectionItem) contentDao.getRootItem(user);
        
        Calendar testCal = helper.getCalendar("timezone.ics");
        
        CalendarCollectionStamp calendarStamp = new HibCalendarCollectionStamp(root);
        calendarStamp.setDescription("description");
        calendarStamp.setTimezoneCalendar(testCal);
        calendarStamp.setLanguage("en");
        calendarStamp.setColor("#123123");
        calendarStamp.setVisibility(true);
        
        root.addStamp(calendarStamp);
        
        contentDao.updateCollection(root);
        clearSession();
        
        root = (CollectionItem) contentDao.findItemByUid(root.getUid());
        
        ContentItem item = generateTestContent();
        EventStamp event = new HibEventStamp();
        event.setEventCalendar(helper.getCalendar("cal1.ics"));
        item.addStamp(event);
        
        contentDao.createContent(root, item);
        
        clearSession();
        
        CollectionItem queryCol = (CollectionItem) contentDao.findItemByUid(root.getUid());
        assertEquals(1, queryCol.getStamps().size());
        Stamp stamp = queryCol.getStamp(CalendarCollectionStamp.class);
        assertTrue(stamp instanceof CalendarCollectionStamp);
        assertEquals("calendar", stamp.getType());
        CalendarCollectionStamp ccs = (CalendarCollectionStamp) stamp;
        assertEquals("description", ccs.getDescription());
        assertEquals(testCal.toString(), ccs.getTimezoneCalendar().toString());
        assertEquals("en", ccs.getLanguage());
        assertEquals("#123123", ccs.getColor());
        assertEquals(true, ccs.getVisibility());
        
        Calendar cal = new EntityConverter(null).convertCollection(queryCol);
        assertEquals(1, cal.getComponents().getComponents(Component.VEVENT).size());
    }
    
    /**
     * Test calendar collection stamp validation.
     * @throws Exception - if something is wrong this exception is thrown.
     */
    @Test
    public void testCalendarCollectionStampValidation() throws Exception {
        User user = getUser(userDao, "testuser");
        CollectionItem root = (CollectionItem) contentDao.getRootItem(user);
        
        Calendar testCal = helper.getCalendar("cal1.ics");
        
        CalendarCollectionStamp calendarStamp = new HibCalendarCollectionStamp(root);
        calendarStamp.setTimezoneCalendar(testCal);
        
        root.addStamp(calendarStamp);
        
        try {
            contentDao.updateCollection(root);
            clearSession();
            fail("able to save invalid timezone, is TimezoneValidator active?");
        } catch (ConstraintViolationException cve) {
            
        } 
    }
    
    /**
     * Test calendar collection stamp validation.
     * @throws Exception - if something is wrong this exception is thrown.
     */
    @Test
    public void testCalendarCollectionStampColorValidation() throws Exception {
        User user = getUser(userDao, "testuser");
        CollectionItem root = (CollectionItem) contentDao.getRootItem(user);
        
        helper.getCalendar("cal1.ics");
        
        CalendarCollectionStamp calendarStamp = new HibCalendarCollectionStamp(root);
        calendarStamp.setColor("red");
        
        root.addStamp(calendarStamp);
        
        try {
            contentDao.updateCollection(root);
            clearSession();
            fail("able to save invalid color, is ColorValidator active?");
        } catch (ConstraintViolationException cve) {
            
        } 
    }
    
    @Test()
    public void shouldNotAllowDisplayNamesWithLengthGreaterThan64() throws Exception {
        User user = getUser(userDao, "testuser");
        CollectionItem root = (CollectionItem) contentDao.getRootItem(user);
        
        CalendarCollectionStamp calendarStamp = new HibCalendarCollectionStamp(root);
        calendarStamp.setDisplayName("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        root.addStamp(calendarStamp);

        assertThrows(ConstraintViolationException.class, () -> {
            contentDao.updateCollection(root);
            clearSession();
        });
    }
    
    @Test()
    public void shouldNotAllowEmptyDisplayNames() throws Exception {
        User user = getUser(userDao, "testuser");
        CollectionItem root = (CollectionItem) contentDao.getRootItem(user);
        
        CalendarCollectionStamp calendarStamp = new HibCalendarCollectionStamp(root);
        calendarStamp.setDisplayName("");
        root.addStamp(calendarStamp);
        
        assertThrows(ConstraintViolationException.class, () -> {
            contentDao.updateCollection(root);
            clearSession();
        });
    }
    
    public void shouldAllowLegalDisplayNames() throws Exception {
        User user = getUser(userDao, "testuser");
        CollectionItem root = (CollectionItem) contentDao.getRootItem(user);
        
        
        CalendarCollectionStamp calendarStamp = new HibCalendarCollectionStamp(root);
        calendarStamp.setDisplayName("Valid display name");
        root.addStamp(calendarStamp);
        try{
            contentDao.updateCollection(root);
        }catch(ConstraintViolationException ex){
            fail("Valid display name was used");
        }
        clearSession();
    }
    /**
     * Test event exception stamp.
     * @throws Exception - if something is wrong this exception is thrown.
     */
    @Test
    public void testEventExceptionStamp() throws Exception {
        User user = getUser(userDao, "testuser");
        CollectionItem root = (CollectionItem) contentDao.getRootItem(user);

        NoteItem item = generateTestContent();
        
        item.setIcalUid("icaluid");
        item.setBody("this is a body");
        
        EventExceptionStamp eventex = new HibEventExceptionStamp();
        eventex.setEventCalendar(helper.getCalendar("exception.ics"));
        
        item.addStamp(eventex);
        
        ContentItem newItem = contentDao.createContent(root, item);
        clearSession();

        ContentItem queryItem = (ContentItem) contentDao.findItemByUid(newItem.getUid());
        assertEquals(1, queryItem.getStamps().size());
       
        Stamp stamp = queryItem.getStamp(EventExceptionStamp.class);
        assertNotNull(stamp.getCreationDate());
        assertNotNull(stamp.getModifiedDate());
        assertTrue(stamp.getCreationDate().equals(stamp.getModifiedDate()));
        assertTrue(stamp instanceof EventExceptionStamp);
        assertEquals("eventexception", stamp.getType());
        EventExceptionStamp ees = (EventExceptionStamp) stamp;
        assertEquals(ees.getEventCalendar().toString(), eventex.getEventCalendar()
                .toString());
    }
    
    /**
     * Test event exception stamp validation.
     * @throws Exception - if something is wrong this exception is thrown.
     */
    @Test
    public void testEventExceptionStampValidation() throws Exception {
        User user = getUser(userDao, "testuser");
        CollectionItem root = (CollectionItem) contentDao.getRootItem(user);

        NoteItem item = generateTestContent();
        
        item.setIcalUid("icaluid");
        item.setBody("this is a body");
        
        EventExceptionStamp eventex = new HibEventExceptionStamp();
        eventex.setEventCalendar(helper.getCalendar("cal1.ics"));
        
        item.addStamp(eventex);
        
        try {
            contentDao.createContent(root, item);
            clearSession();
            fail("able to save invalid exception event, is TimezoneValidator active?");
        } catch (ConstraintViolationException cve) {
        }
    }

    /**
     * Gets user.
     * @param userDao The userDao.
     * @param username The username.
     * @return The user.
     */
    private User getUser(UserDao userDao, String username) {
        return helper.getUser(userDao, contentDao, username);
    }

    /**
     * Generates test content.
     * @return The note item.
     * @throws Exception - if something is wrong this exception is thrown.
     */
    private NoteItem generateTestContent() throws Exception {
        return generateTestContent("test", "testuser");
    }

    /**
     * Generates test content.
     * @param name The name. 
     * @param owner The owner.
     * @return The note item.
     * @throws Exception - if something is wrong this exception is thrown.
     */
    private NoteItem generateTestContent(String name, String owner)
            throws Exception {
        NoteItem content = new HibNoteItem();
        content.setName(name);
        content.setDisplayName(name);
        content.setOwner(getUser(userDao, owner));
        content.addAttribute(new HibStringAttribute(new HibQName("customattribute"),
                "customattributevalue"));
        return content;
    }

}
